package com.telme.intent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.entity.ChatMessage;
import com.telme.chat.service.ChatContext;
import com.telme.consult.entity.ConsultCondition;
import com.telme.consult.entity.ConsultRequest;
import com.telme.consult.repository.ConsultRequestRepository;
import com.telme.global.common.exception.GeneralException;
import com.telme.intent.converter.IntentConverter;
import com.telme.intent.dto.res.FollowUpRouteResponse;
import com.telme.intent.dto.res.FollowUpRouteResponse.Disposition;
import com.telme.intent.dto.res.IntentRouteResponse;
import com.telme.intent.dto.res.IntentRouteResponse.IntentSubQueryResponse;
import com.telme.intent.dto.res.LlmFollowUpPayload;
import com.telme.intent.dto.res.LlmFollowUpPayload.ConditionPayload;
import com.telme.intent.dto.res.LlmFollowUpPayload.ResponseType;
import com.telme.intent.dto.res.LlmRoutingPayload;
import com.telme.intent.entity.QueryRouting;
import com.telme.intent.exception.IntentErrorCode;
import com.telme.intent.repository.QueryRoutingRepository;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.dto.req.ResponseFormat;
import com.telme.llm.entity.LlmGeneration.TaskType;
import com.telme.llm.service.LlmClient;
import com.telme.llm.exception.LlmErrorCode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientException;

@Service
@Slf4j
public class QueryRoutingService {

    // LLM이 정의 밖의 키를 만들어내도 여기서 걸러진다
    private static final Set<String> KNOWN_CONDITION_KEYS = Set.of(
        FollowUpRouteResponse.LOCATION_KEY, FollowUpRouteResponse.SERVICE_TYPE_KEY);

    // 상담 모듈의 DialogueInput.Condition이 255자를 넘기면 예외를 던진다
    private static final int MAX_CONDITION_VALUE_LENGTH = 255;

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final QueryRoutingRepository queryRoutingRepository;
    private final ConsultRequestRepository consultRequestRepository;
    private final RuleBasedRoutingFallback ruleBasedFallback;
    private final IntentConverter intentConverter;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    public QueryRoutingService(
            LlmClient llmClient,
            ObjectMapper objectMapper,
            QueryRoutingRepository queryRoutingRepository,
            ConsultRequestRepository consultRequestRepository,
            RuleBasedRoutingFallback ruleBasedFallback,
            IntentConverter intentConverter,
            TransactionTemplate transactionTemplate) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
        this.queryRoutingRepository = queryRoutingRepository;
        this.consultRequestRepository = consultRequestRepository;
        this.ruleBasedFallback = ruleBasedFallback;
        this.intentConverter = intentConverter;
        this.transactionTemplate = transactionTemplate;
    }

    // Context 없이 질문만으로 분류한다. 멀티턴 지시어를 풀어야 하면 아래 오버로드를 쓴다
    public IntentRouteResponse route(ChatMessage userMessage) {
        return route(userMessage, null);
    }

    public IntentRouteResponse route(ChatMessage userMessage, ChatContext context) {
        return route(userMessage, context, false);
    }

    /** 단일 상담 연결용 진입점. 복합 질문은 저장하기 전에 차단한다. */
    public IntentRouteResponse routeSingleConsult(ChatMessage userMessage, ChatContext context) {
        return route(userMessage, context, true);
    }

    private IntentRouteResponse route(
            ChatMessage userMessage, ChatContext context, boolean singleConsultOnly) {
        if (userMessage == null) {
            throw new IllegalArgumentException("사용자 메시지는 필수입니다.");
        }

        if (userMessage.getMessageId() != null) {
            Optional<QueryRouting> existing = queryRoutingRepository.findByMessage_MessageId(userMessage.getMessageId());
            if (existing.isPresent()) {
                log.info("[라우팅] 이미 라우팅된 메시지입니다. 기존 결과를 반환합니다: messageId={}", userMessage.getMessageId());
                IntentRouteResponse result =
                    runInTransaction(() -> buildExistingResponse(existing.get(), userMessage.getMessageId()));
                ensureSingleConsultSupported(result, singleConsultOnly);
                return result;
            }
        }

        String question = userMessage.getContent() != null ? userMessage.getContent().trim() : "";

        if (question.isBlank()) {
            log.info("[라우팅] 질문 내용이 비어 있어 UNKNOWN으로 처리합니다.");
            LlmRoutingPayload fallbackPayload = ruleBasedFallback.classify(question);
            ensureSingleConsultSupported(fallbackPayload, singleConsultOnly);
            return executeInTransaction(userMessage, fallbackPayload, QueryRouting.Method.RULE);
        }

        LlmRoutingPayload payload;
        QueryRouting.Method method;

        try {
            LlmRequest request = LlmRequest.builder()
                .taskType(TaskType.ROUTING)
                .systemPrompt(RoutingPromptTemplates.ROUTING_SYSTEM_PROMPT)
                .userPrompt(RoutingPromptTemplates.routingUserPrompt(context, question))
                .format(ResponseFormat.JSON)
                .temperature(0.1)
                .maxTokens(500)
                .build();

            String json = llmClient.generate(request);
            if (json == null || json.isBlank()) {
                throw new GeneralException(IntentErrorCode.LLM_RESPONSE_PARSE_FAILED);
            }

            try {
                payload = objectMapper.readValue(json, LlmRoutingPayload.class);
            } catch (JsonProcessingException e) {
                throw new GeneralException(IntentErrorCode.LLM_RESPONSE_PARSE_FAILED);
            }

            if (payload == null) {
                throw new GeneralException(IntentErrorCode.LLM_RESPONSE_PARSE_FAILED);
            }

            method = QueryRouting.Method.LLM;

            log.info("[라우팅] LLM 분류 완료 -> intent={}, confidence={}",
                    payload.intent(), payload.confidence());

        } catch (GeneralException e) {
            if (e.getErrorCode() instanceof LlmErrorCode
                    || e.getErrorCode() == IntentErrorCode.LLM_CONNECTION_FAILED
                    || e.getErrorCode() == IntentErrorCode.LLM_RESPONSE_PARSE_FAILED) {
                log.warn("[라우팅] LLM 오류 또는 파싱 실패, Rule Fallback으로 전환: {}", e.getMessage());
                payload = ruleBasedFallback.classify(question);
                method = QueryRouting.Method.RULE;
            } else {
                throw e;
            }
        } catch (RestClientException e) {
            log.warn("[라우팅] LLM 호출 실패, Rule Fallback으로 전환: {}", e.getMessage());
            payload = ruleBasedFallback.classify(question);
            method = QueryRouting.Method.RULE;
        }

        ensureSingleConsultSupported(payload, singleConsultOnly);
        return executeInTransaction(userMessage, payload, method);
    }

    // route()와 달리 새 상담 요청을 만들지 않는다. 조건 반영(PENDING -> FILLED)은 상담 도메인이 한다
    public FollowUpRouteResponse analyzeFollowUp(Long sessionId, String followUpText) {
        if (sessionId == null) {
            throw new IllegalArgumentException("세션 ID는 필수입니다.");
        }

        WaitingConsult waiting = runInTransaction(() -> loadWaitingConsult(sessionId));
        if (waiting == null) {
            log.info("[후속분석] 되묻기 대기 중인 상담 요청이 없습니다: sessionId={}", sessionId);
            return FollowUpRouteResponse.noTarget();
        }

        String reply = followUpText != null ? followUpText.trim() : "";
        if (reply.isBlank()) {
            log.info("[후속분석] 후속 답변이 비어 있어 조건 없이 반환합니다: consultRequestId={}", waiting.consultRequestId());
            return new FollowUpRouteResponse(
                waiting.consultRequestId(), Collections.emptyMap(), Collections.emptySet(),
                QueryRouting.Method.RULE);
        }

        LlmFollowUpPayload payload;
        QueryRouting.Method method;

        try {
            payload = requestFollowUpAnalysis(reply, waiting.pendingKeys());
            method = QueryRouting.Method.LLM;
            log.info("[후속분석] LLM 조건 추출 완료: consultRequestId={}, 추출 건수={}",
                    waiting.consultRequestId(), payload.conditions().size());
        } catch (GeneralException e) {
            if (e.getErrorCode() instanceof LlmErrorCode
                    || e.getErrorCode() == IntentErrorCode.LLM_CONNECTION_FAILED
                    || e.getErrorCode() == IntentErrorCode.LLM_RESPONSE_PARSE_FAILED) {
                log.warn("[후속분석] LLM 오류 또는 파싱 실패, Rule Fallback으로 전환: {}", e.getMessage());
                payload = ruleBasedFallback.classifyFollowUp(reply, waiting.pendingKeys());
                method = QueryRouting.Method.RULE;
            } else {
                throw e;
            }
        } catch (RestClientException e) {
            log.warn("[후속분석] LLM 호출 실패, Rule Fallback으로 전환: {}", e.getMessage());
            payload = ruleBasedFallback.classifyFollowUp(reply, waiting.pendingKeys());
            method = QueryRouting.Method.RULE;
        }

        ExtractedConditions extracted = toExtractedConditions(payload);

        // 조건이 하나도 안 잡히면 되묻기가 반복되므로 규칙으로 한 번 더 시도한다
        if (extracted.isEmpty() && method == QueryRouting.Method.LLM) {
            LlmFollowUpPayload rulePayload =
                ruleBasedFallback.classifyFollowUp(reply, waiting.pendingKeys());
            ExtractedConditions ruleConditions = toExtractedConditions(rulePayload);
            if (!ruleConditions.isEmpty()
                    || rulePayload.responseType() == ResponseType.NEW_QUESTION) {
                payload = rulePayload;
                extracted = ruleConditions;
                method = QueryRouting.Method.RULE;
            }
        }

        return new FollowUpRouteResponse(
            waiting.consultRequestId(),
            extracted.values(),
            extracted.declinedKeys(),
            method,
            disposition(payload.responseType(), extracted));
    }

    private Disposition disposition(ResponseType responseType, ExtractedConditions extracted) {
        if (!extracted.isEmpty()) {
            return Disposition.CONDITION_RESPONSE;
        }
        return responseType == ResponseType.NEW_QUESTION
            ? Disposition.NEW_QUESTION
            : Disposition.DEFERRED;
    }

    private void ensureSingleConsultSupported(
            LlmRoutingPayload payload, boolean singleConsultOnly) {
        if (!singleConsultOnly || payload == null) {
            return;
        }
        int subQueryCount = payload.subQueries() == null ? 0 : payload.subQueries().size();
        if (payload.intent() == QueryRouting.Intent.BOTH || subQueryCount > 1) {
            throw new IllegalStateException("현재 Chat 상담 연결은 단일 하위 질문만 지원합니다.");
        }
    }

    private void ensureSingleConsultSupported(
            IntentRouteResponse response, boolean singleConsultOnly) {
        if (!singleConsultOnly || response == null) {
            return;
        }
        int subQueryCount = response.subQueries() == null ? 0 : response.subQueries().size();
        if (response.intent() == QueryRouting.Intent.BOTH || subQueryCount > 1) {
            throw new IllegalStateException("현재 Chat 상담 연결은 단일 하위 질문만 지원합니다.");
        }
    }

    private LlmFollowUpPayload requestFollowUpAnalysis(String reply, Set<String> pendingKeys) {
        LlmRequest request = LlmRequest.builder()
            .taskType(TaskType.ROUTING)
            .systemPrompt(RoutingPromptTemplates.FOLLOW_UP_SYSTEM_PROMPT)
            .userPrompt(RoutingPromptTemplates.followUpUserPrompt(pendingKeys, reply))
            .format(ResponseFormat.JSON)
            .temperature(0.0)
            .maxTokens(300)
            .build();

        String json = llmClient.generate(request);
        if (json == null || json.isBlank()) {
            throw new GeneralException(IntentErrorCode.LLM_RESPONSE_PARSE_FAILED);
        }

        try {
            LlmFollowUpPayload payload = objectMapper.readValue(json, LlmFollowUpPayload.class);
            if (payload == null) {
                throw new GeneralException(IntentErrorCode.LLM_RESPONSE_PARSE_FAILED);
            }
            return payload;
        } catch (JsonProcessingException e) {
            throw new GeneralException(IntentErrorCode.LLM_RESPONSE_PARSE_FAILED);
        }
    }

    private WaitingConsult loadWaitingConsult(Long sessionId) {
        return consultRequestRepository
            .findFirstBySession_SessionIdAndStatusOrderBySubqueryOrderAsc(
                sessionId, ConsultRequest.Status.WAITING_CONDITION)
            .map(request -> new WaitingConsult(request.getConsultRequestId(), pendingKeysOf(request)))
            .orElse(null);
    }

    // 되묻는 조건을 알 수 없으면 지역으로 본다(현재 DialogueService는 지역만 묻는다)
    // 거절 판정이 이 집합 전체에 적용되므로 KNOWN_CONDITION_KEYS처럼 넓게 잡으면 안 된다
    private Set<String> pendingKeysOf(ConsultRequest request) {
        List<ConsultCondition> conditions = request.getConditions();
        if (conditions == null) {
            return Set.of(FollowUpRouteResponse.LOCATION_KEY);
        }
        Set<String> pending = conditions.stream()
            .filter(condition -> condition.getStatus() == ConsultCondition.Status.PENDING)
            .map(ConsultCondition::getConditionKey)
            .filter(key -> key != null && !key.isBlank())
            .collect(Collectors.toCollection(LinkedHashSet::new));
        return pending.isEmpty() ? Set.of(FollowUpRouteResponse.LOCATION_KEY) : pending;
    }

    private ExtractedConditions toExtractedConditions(LlmFollowUpPayload payload) {
        if (payload == null || payload.conditions().isEmpty()) {
            return ExtractedConditions.empty();
        }

        Map<String, String> values = new LinkedHashMap<>();
        Set<String> declinedKeys = new LinkedHashSet<>();

        for (ConditionPayload condition : payload.conditions()) {
            if (condition == null || condition.key() == null || condition.status() == null) {
                continue;
            }

            String key = condition.key().trim();
            if (!KNOWN_CONDITION_KEYS.contains(key)) {
                log.debug("[후속분석] 정의되지 않은 조건 키를 무시합니다: {}", key);
                continue;
            }

            if (condition.status() == LlmFollowUpPayload.Status.DECLINED) {
                declinedKeys.add(key);
                continue;
            }

            String value = condition.value() != null ? condition.value().trim() : "";
            if (value.isBlank() || value.length() > MAX_CONDITION_VALUE_LENGTH) {
                continue;
            }
            values.put(key, value);
        }
        return new ExtractedConditions(values, declinedKeys);
    }

    private record ExtractedConditions(Map<String, String> values, Set<String> declinedKeys) {

        static ExtractedConditions empty() {
            return new ExtractedConditions(Collections.emptyMap(), Collections.emptySet());
        }

        boolean isEmpty() {
            return values.isEmpty() && declinedKeys.isEmpty();
        }
    }

    private record WaitingConsult(Long consultRequestId, Set<String> pendingKeys) {}

    private <T> T runInTransaction(Supplier<T> action) {
        return transactionTemplate.execute(status -> action.get());
    }

    private IntentRouteResponse executeInTransaction(
            ChatMessage userMessage,
            LlmRoutingPayload payload,
            QueryRouting.Method method) {
        return runInTransaction(() -> saveAndBuildResult(userMessage, payload, method));
    }

    private IntentRouteResponse saveAndBuildResult(
            ChatMessage message,
            LlmRoutingPayload payload,
            QueryRouting.Method method) {

        if (payload == null) {
            payload = ruleBasedFallback.classify(message != null ? message.getContent() : "");
        }

        QueryRouting routing = intentConverter.toQueryRouting(message, payload, method);
        try {
            routing = queryRoutingRepository.save(routing);
        } catch (DataIntegrityViolationException e) {
            log.warn("[라우팅] 동시 저장 충돌 발생 (messageId={}). 기존 라우팅 결과를 반환합니다.", message.getMessageId());
            return queryRoutingRepository.findByMessage_MessageId(message.getMessageId())
                .map(r -> buildExistingResponse(r, message.getMessageId()))
                .orElseThrow(() -> e);
        }

        List<IntentSubQueryResponse> subQueries = new ArrayList<>();
        List<LlmRoutingPayload.SubQueryPayload> subQueryPayloads = payload.subQueries() != null
                ? payload.subQueries()
                : Collections.emptyList();

        // 상위 의도가 있는데 분해 결과가 비면 상담 요청이 하나도 안 만들어져 이후 처리가 통째로 빠진다
        if (subQueryPayloads.isEmpty()) {
            subQueryPayloads = defaultSubQueries(payload, message);
            if (!subQueryPayloads.isEmpty()) {
                log.info("[라우팅] 하위 질의가 비어 기본 하위 질의를 생성합니다: intent={}, 생성 건수={}",
                        payload.intent(), subQueryPayloads.size());
            }
        }

        for (int i = 0; i < subQueryPayloads.size(); i++) {
            LlmRoutingPayload.SubQueryPayload sub = subQueryPayloads.get(i);
            if (sub == null) {
                continue;
            }

            ConsultRequest.Intent subIntent = sub.intent() != null
                ? sub.intent()
                : ConsultRequest.Intent.FAQ;

            short order = (short) (sub.order() != null && sub.order() > 0 ? sub.order() : (i + 1));
            String queryText = (sub.queryText() != null && !sub.queryText().isBlank())
                ? sub.queryText()
                : (payload.refinedQuery() != null && !payload.refinedQuery().isBlank()
                    ? payload.refinedQuery()
                    : (message != null && message.getContent() != null ? message.getContent() : ""));

            ConsultRequest request = intentConverter.toConsultRequest(
                message, order, subIntent, queryText
            );

            if (sub.conditions() != null) {
                sub.conditions().forEach((key, value) -> {
                    if (key != null && !key.isBlank() && value != null && !value.isBlank()) {
                        ConsultCondition condition = intentConverter.toConsultCondition(request, key, value);
                        if (request.getConditions() != null) {
                            request.getConditions().add(condition);
                        }
                    }
                });
            }

            ConsultRequest savedRequest = consultRequestRepository.save(request);

            subQueries.add(intentConverter.toSubQueryResponse(
                savedRequest,
                order,
                subIntent,
                queryText,
                sub.conditions() != null ? sub.conditions() : Collections.emptyMap()
            ));
        }

        Map<String, String> extractedConditions = payload.extractedConditions() != null
                ? payload.extractedConditions()
                : Collections.emptyMap();

        return intentConverter.toIntentRouteResponse(routing, extractedConditions, subQueries);
    }

    // LLM이 intent만 주고 subQueries를 비워 보내도 상담 요청은 만들어져야 한다
    private List<LlmRoutingPayload.SubQueryPayload> defaultSubQueries(
            LlmRoutingPayload payload, ChatMessage message) {

        String queryText = payload.refinedQuery() != null && !payload.refinedQuery().isBlank()
                ? payload.refinedQuery()
                : (message != null && message.getContent() != null ? message.getContent().trim() : "");

        // UNKNOWN은 상담할 내용이 없고, 질의 문구가 없으면 만들 수 있는 하위 질의도 없다
        if (queryText.isBlank() || payload.intent() == QueryRouting.Intent.UNKNOWN) {
            return Collections.emptyList();
        }

        Map<String, String> conditions = payload.extractedConditions() != null
                ? payload.extractedConditions()
                : Collections.emptyMap();

        return switch (payload.intent()) {
            case FAQ -> List.of(new LlmRoutingPayload.SubQueryPayload(
                    (short) 1, ConsultRequest.Intent.FAQ, queryText, Collections.emptyMap()));
            case STORE -> List.of(new LlmRoutingPayload.SubQueryPayload(
                    (short) 1, ConsultRequest.Intent.STORE, queryText, conditions));
            // BOTH는 한쪽만 만들면 나머지 의도의 상담이 누락된다
            case BOTH -> List.of(
                    new LlmRoutingPayload.SubQueryPayload(
                            (short) 1, ConsultRequest.Intent.FAQ, queryText, Collections.emptyMap()),
                    new LlmRoutingPayload.SubQueryPayload(
                            (short) 2, ConsultRequest.Intent.STORE, queryText, conditions));
            case UNKNOWN -> Collections.emptyList();
        };
    }

    private IntentRouteResponse buildExistingResponse(QueryRouting routing, Long messageId) {
        Map<String, String> extractedConditions = intentConverter.parseConditions(routing.getExtractedConditions());
        List<ConsultRequest> requests = consultRequestRepository.findByOriginMessage_MessageIdOrderBySubqueryOrderAsc(messageId);

        List<IntentSubQueryResponse> subQueries = new ArrayList<>();
        for (ConsultRequest req : requests) {
            Map<String, String> condMap = req.getConditions() != null
                ? req.getConditions().stream().collect(Collectors.toMap(
                    ConsultCondition::getConditionKey,
                    ConsultCondition::getConditionValue,
                    (a, b) -> a
                ))
                : Collections.emptyMap();

            subQueries.add(intentConverter.toSubQueryResponse(
                req,
                req.getSubqueryOrder(),
                req.getIntent(),
                req.getQueryText(),
                condMap
            ));
        }

        return intentConverter.toIntentRouteResponse(routing, extractedConditions, subQueries);
    }
}
