package com.telme.intent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.entity.ChatMessage;
import com.telme.chat.service.ChatContext;
import com.telme.consult.entity.ConsultCondition;
import com.telme.consult.entity.ConsultRequest;
import com.telme.consult.dto.PlanChangeConditions;
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
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
        FollowUpRouteResponse.LOCATION_KEY, FollowUpRouteResponse.SERVICE_TYPE_KEY,
        PlanChangeConditions.JOINED, PlanChangeConditions.CHANGED);

    // 상담 모듈의 DialogueInput.Condition이 255자를 넘기면 예외를 던진다
    private static final int MAX_CONDITION_VALUE_LENGTH = 255;
    private static final BigDecimal MIN_USABLE_CONFIDENCE = new BigDecimal("0.5");
    private static final Set<String> SERVICE_TYPES = Set.of(
        "NEW_LINE", "PORT_IN", "NAME_CHANGE", "USIM_REISSUE");
    private static final Pattern NUMBER = Pattern.compile(
        "\\d+(?:[.,]\\d+)?\\s*(?:TB|GB|MB|KB|G|테라바이트|기가바이트|메가바이트|테라|기가|메가|만원|원|개월|달|년|일|시간|분|회|개|%)?",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern PLACE = Pattern.compile(
        "(?<![가-힣A-Za-z0-9])[가-힣A-Za-z0-9]{2,14}(?:역|읍|면|공항|터미널|사거리)");
    private static final Pattern CONDITIONAL_ENDING = Pattern.compile("(?:하면|려면|되면|으면)$");
    private static final Pattern ADMIN_AREA_BEFORE = Pattern.compile("(?:시|군|구)\\s+$");
    private static final Pattern LOCATION_PARTICLE_AFTER = Pattern.compile("^(?:에서|에|으로|로)");
    private static final Pattern CONTEXT_REFERENCE = Pattern.compile(
        "그거|그건|거기|그\\s*지역|아까|앞서|그때|이거|저거|방금|그러면|그럼");

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

    /** 단일 상담 연결용 진입점. 다중 FAQ 질의는 합치고 FAQ와 STORE 복합 질문은 저장 전에 차단한다. */
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

            payload = normalizeLlmPayload(payload, question, context);

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

        if (method == QueryRouting.Method.LLM
                && payload.intent() != QueryRouting.Intent.UNKNOWN
                && payload.confidence().compareTo(MIN_USABLE_CONFIDENCE) < 0) {
            log.info("[라우팅] 낮은 분류 신뢰도로 상담을 보류합니다: messageId={}, confidence={}",
                    userMessage.getMessageId(), payload.confidence());
            payload = new LlmRoutingPayload(
                    QueryRouting.Intent.UNKNOWN, payload.confidence(), question,
                    Collections.emptyMap(), Collections.emptyList());
        }

        payload = combineFaqSubQueriesForSingleConsult(payload, question, context, singleConsultOnly);
        ensureSingleConsultSupported(payload, singleConsultOnly);
        IntentRouteResponse result = executeInTransaction(userMessage, payload, method);
        ensureSingleConsultSupported(result, singleConsultOnly);
        return result;
    }

    private LlmRoutingPayload combineFaqSubQueriesForSingleConsult(
            LlmRoutingPayload payload, String question, ChatContext context, boolean singleConsultOnly) {
        if (!singleConsultOnly
                || payload.intent() != QueryRouting.Intent.FAQ
                || payload.subQueries().size() <= 1
                || payload.subQueries().stream().anyMatch(
                        subQuery -> subQuery == null || subQuery.intent() != ConsultRequest.Intent.FAQ)) {
            return payload;
        }

        String queryText = payload.refinedQuery() != null && !payload.refinedQuery().isBlank()
                ? payload.refinedQuery().strip()
                : question;
        // 분해된 질문을 다시 요약하면 일부 대상이 빠질 수 있으므로 원문을 검색에 사용한다.
        if (context == null
                || (context.summary() == null && context.history().isEmpty())
                || !CONTEXT_REFERENCE.matcher(question).find()) {
            queryText = question;
        }
        return new LlmRoutingPayload(
                payload.intent(), payload.confidence(), payload.refinedQuery(),
                payload.extractedConditions(),
                List.of(new LlmRoutingPayload.SubQueryPayload(
                        (short) 1, ConsultRequest.Intent.FAQ, queryText, Collections.emptyMap())));
    }

    private LlmRoutingPayload normalizeLlmPayload(
            LlmRoutingPayload payload, String question, ChatContext context) {
        if (payload.confidence().compareTo(BigDecimal.ZERO) < 0
                || payload.confidence().compareTo(BigDecimal.ONE) > 0
                || payload.subQueries().size() > Short.MAX_VALUE) {
            throw new GeneralException(IntentErrorCode.LLM_RESPONSE_PARSE_FAILED);
        }

        Map<String, String> extracted = payload.intent() == QueryRouting.Intent.UNKNOWN
                ? Collections.emptyMap()
                : validConditions(payload.extractedConditions(), question, context);
        List<LlmRoutingPayload.SubQueryPayload> normalized = new ArrayList<>();
        boolean hasFaq = false;
        boolean hasStore = false;
        for (LlmRoutingPayload.SubQueryPayload sub : payload.subQueries()) {
            if (sub == null) {
                throw new GeneralException(IntentErrorCode.LLM_RESPONSE_PARSE_FAILED);
            }
            ConsultRequest.Intent intent = sub.intent();
            if (intent == null) {
                intent = switch (payload.intent()) {
                    case FAQ -> ConsultRequest.Intent.FAQ;
                    case STORE -> ConsultRequest.Intent.STORE;
                    default -> null;
                };
            }
            if (intent == null) {
                throw new GeneralException(IntentErrorCode.LLM_RESPONSE_PARSE_FAILED);
            }
            hasFaq |= intent == ConsultRequest.Intent.FAQ;
            hasStore |= intent == ConsultRequest.Intent.STORE;
            Map<String, String> conditions = intent == ConsultRequest.Intent.STORE
                    ? validConditions(sub.conditions(), question, context)
                    : Collections.emptyMap();
            normalized.add(new LlmRoutingPayload.SubQueryPayload(
                    (short) (normalized.size() + 1), intent, sub.queryText(), conditions));
        }

        boolean inconsistent = switch (payload.intent()) {
            case FAQ -> hasStore;
            case STORE -> hasFaq;
            case BOTH -> !normalized.isEmpty() && (!hasFaq || !hasStore);
            case UNKNOWN -> !normalized.isEmpty();
        };
        if (inconsistent) {
            throw new GeneralException(IntentErrorCode.LLM_RESPONSE_PARSE_FAILED);
        }
        long storeCount = normalized.stream()
                .filter(sub -> sub.intent() == ConsultRequest.Intent.STORE)
                .count();
        String refined = safeQueryText(payload.refinedQuery(), question, context, true);
        List<LlmRoutingPayload.SubQueryPayload> safeSubQueries = new ArrayList<>();
        for (LlmRoutingPayload.SubQueryPayload sub : normalized) {
            String queryText = safeQueryText(
                    sub.queryText(), question, context,
                    payload.intent() == QueryRouting.Intent.FAQ && normalized.size() == 1);
            Map<String, String> conditions = sub.conditions();
            if (sub.intent() == ConsultRequest.Intent.STORE && storeCount == 1) {
                Map<String, String> merged = new LinkedHashMap<>(extracted);
                merged.putAll(conditions);
                conditions = merged;
            }
            safeSubQueries.add(new LlmRoutingPayload.SubQueryPayload(
                    sub.order(), sub.intent(), queryText, conditions));
        }
        return new LlmRoutingPayload(
                payload.intent(), payload.confidence(), refined, extracted, safeSubQueries);
    }

    private String safeQueryText(
            String candidate, String question, ChatContext context, boolean preserveNumbers) {
        if (candidate == null || candidate.isBlank()) {
            return candidate;
        }
        if (hasUnsupportedToken(NUMBER, candidate, referenceText(NUMBER, question, context))
                || hasUnsupportedToken(PLACE, candidate, referenceText(PLACE, question, context))
                || (preserveNumbers && hasUnsupportedToken(NUMBER, question, candidate))) {
            log.info("[라우팅] 검색 질문의 수치 또는 지역이 원문과 맞지 않아 원문을 사용합니다.");
            return question;
        }
        return candidate.strip();
    }

    private boolean hasUnsupportedToken(Pattern pattern, String source, String reference) {
        Set<String> referenceTokens = tokens(pattern, reference);
        Set<String> sourceTokens = tokens(pattern, source, pattern == PLACE);
        if (pattern == PLACE) {
            sourceTokens.removeIf(token -> containsPlacePhrase(reference, token));
        }
        return !referenceTokens.containsAll(sourceTokens);
    }

    private boolean containsPlacePhrase(String text, String placePhrase) {
        if (text == null) {
            return false;
        }
        Pattern phrase = Pattern.compile("(?<![가-힣A-Za-z0-9])" + Pattern.quote(placePhrase));
        return phrase.matcher(text).find();
    }

    private Set<String> tokens(Pattern pattern, String text) {
        return tokens(pattern, text, false);
    }

    private Set<String> tokens(Pattern pattern, String text, boolean candidatePlace) {
        Set<String> values = new LinkedHashSet<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String value = matcher.group();
            if (pattern == PLACE && !looksLikePlace(value, text, matcher.start(), matcher.end(), candidatePlace)) {
                continue;
            }
            values.add(pattern == NUMBER
                    ? value.replace(",", "").replaceAll("\\s+", "").toUpperCase(Locale.ROOT)
                    : value);
        }
        return values;
    }

    private boolean looksLikePlace(String value, String text, int start, int end, boolean candidatePlace) {
        if (value.endsWith("지역") || value.endsWith("영역") || value.endsWith("내역")) {
            return false;
        }
        // 모델이 만든 검색어는 더 넓게 검사해 단독 '면' 지명도 근거 없이 추가되지 못하게 한다.
        if (candidatePlace && value.endsWith("면")) {
            return !CONDITIONAL_ENDING.matcher(value).find();
        }
        // '면'은 조건 어미와 겹치므로 행정구역 표기나 바로 붙은 장소 조사로만 판별한다.
        return !value.endsWith("면")
                || ADMIN_AREA_BEFORE.matcher(text.substring(0, start)).find()
                || LOCATION_PARTICLE_AFTER.matcher(text.substring(end)).find();
    }

    private String referenceText(Pattern pattern, String question, ChatContext context) {
        // 현재 질문에 새 조건이 명시되면 이전 대화의 같은 종류 조건은 검증 근거로 사용하지 않는다.
        return tokens(pattern, question).isEmpty() ? groundingText(question, context) : question;
    }

    private String groundingText(String question, ChatContext context) {
        if (context == null || !CONTEXT_REFERENCE.matcher(question).find()) {
            return question;
        }
        return question + " " + (context.summary() == null ? "" : context.summary()) + " "
                + context.history().stream()
                        .filter(item -> item.role() == ChatMessage.Role.USER)
                        .map(item -> item.content() == null ? "" : item.content())
                        .collect(Collectors.joining(" "));
    }

    private Map<String, String> validConditions(
            Map<String, String> conditions, String question, ChatContext context) {
        if (conditions == null || conditions.isEmpty()) {
            return Collections.emptyMap();
        }
        String locationEvidence = referenceText(PLACE, question, context);
        String serviceTypeEvidence = ruleBasedFallback.hasServiceTypeMention(question)
                ? question : groundingText(question, context);
        Map<String, String> valid = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : conditions.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue() == null ? "" : entry.getValue().strip();
            if (!Set.of(FollowUpRouteResponse.LOCATION_KEY, FollowUpRouteResponse.SERVICE_TYPE_KEY).contains(key)
                    || value.isBlank()
                    || value.length() > MAX_CONDITION_VALUE_LENGTH) {
                continue;
            }
            if (FollowUpRouteResponse.SERVICE_TYPE_KEY.equals(key)
                    && (!SERVICE_TYPES.contains(value)
                        || !ruleBasedFallback.matchesServiceType(value, serviceTypeEvidence))) {
                continue;
            }
            if (FollowUpRouteResponse.LOCATION_KEY.equals(key)
                    && (!locationEvidence.contains(value)
                        || (!tokens(PLACE, value).isEmpty()
                            && !tokens(PLACE, locationEvidence).containsAll(tokens(PLACE, value))))) {
                continue;
            }
            valid.put(key, value);
        }
        return valid;
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
        if (waiting.pendingKeys().isEmpty()) {
            throw new IllegalStateException("후속 분석에 필요한 대기 조건이 없습니다.");
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

        // 요금제 조건은 실제 사용자 발화로 검증된 값·거절·보류만 받아 모델의 오분류를 보완한다.
        if (waiting.pendingKeys().stream().anyMatch(PlanChangeConditions.KEYS::contains)) {
            var literal = ruleBasedFallback.classifyFollowUp(reply, waiting.pendingKeys());
            if (literal.responseType() == ResponseType.NEW_QUESTION || !literal.conditions().isEmpty()
                    || PlanChangeConditions.isDeferred(reply) || PlanChangeConditions.isAmbiguous(reply)) {
                if (payload.responseType() != literal.responseType()
                        || !toExtractedConditions(payload, reply, waiting.pendingKeys())
                                .equals(toExtractedConditions(literal, reply, waiting.pendingKeys()))) {
                    payload = literal;
                    method = QueryRouting.Method.RULE;
                }
            }
        }

        if (payload.responseType() == ResponseType.NEW_QUESTION
                && waiting.pendingKeys().stream().anyMatch(PlanChangeConditions.KEYS::contains)) {
            return new FollowUpRouteResponse(waiting.consultRequestId(), Map.of(), Set.of(), method, Disposition.NEW_QUESTION);
        }
        ExtractedConditions extracted = toExtractedConditions(payload, reply, waiting.pendingKeys());

        // LLM이 명시적으로 새 질문이라고 판단한 결과는 규칙이 조건 답변으로 덮지 않는다.
        // 그 외에 조건이 하나도 안 잡힌 경우에만 되묻기 반복을 막기 위해 규칙으로 한 번 더 시도한다.
        if (extracted.isEmpty()
                && method == QueryRouting.Method.LLM
                && payload.responseType() != ResponseType.NEW_QUESTION) {
            LlmFollowUpPayload rulePayload =
                ruleBasedFallback.classifyFollowUp(reply, waiting.pendingKeys());
            ExtractedConditions ruleConditions = toExtractedConditions(rulePayload, reply, waiting.pendingKeys());
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
            throw new UnsupportedCompoundQuestionException();
        }
    }

    private void ensureSingleConsultSupported(
            IntentRouteResponse response, boolean singleConsultOnly) {
        if (!singleConsultOnly || response == null) {
            return;
        }
        int subQueryCount = response.subQueries() == null ? 0 : response.subQueries().size();
        if (response.intent() == QueryRouting.Intent.BOTH || subQueryCount > 1) {
            throw new UnsupportedCompoundQuestionException();
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

    // 거절은 현재 대기 조건에만 적용한다. 지역 fallback은 기존 STORE 상담 호환용이다.
    private Set<String> pendingKeysOf(ConsultRequest request) {
        List<ConsultCondition> conditions = request.getConditions();
        if (conditions == null) {
            return request.getIntent() == ConsultRequest.Intent.STORE ? Set.of(FollowUpRouteResponse.LOCATION_KEY) : Set.of();
        }
        Set<String> pending = conditions.stream()
            .filter(condition -> condition.getStatus() == ConsultCondition.Status.PENDING)
            .map(ConsultCondition::getConditionKey)
            .filter(key -> key != null && !key.isBlank())
            .collect(Collectors.toCollection(LinkedHashSet::new));
        return pending.isEmpty() && request.getIntent() == ConsultRequest.Intent.STORE ? Set.of(FollowUpRouteResponse.LOCATION_KEY) : pending;
    }

    private ExtractedConditions toExtractedConditions(LlmFollowUpPayload payload, String reply, Set<String> pendingKeys) {
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

            boolean planConsult = pendingKeys.stream().anyMatch(PlanChangeConditions.KEYS::contains);
            if (planConsult != PlanChangeConditions.KEYS.contains(key)) continue;

            if (condition.status() == LlmFollowUpPayload.Status.DECLINED) {
                if (PlanChangeConditions.KEYS.contains(key) && ruleBasedFallback.classifyFollowUp(reply, pendingKeys).conditions().stream()
                        .noneMatch(item -> key.equals(item.key()) && item.status() == LlmFollowUpPayload.Status.DECLINED)) continue;
                declinedKeys.add(key);
                continue;
            }

            String value = condition.value() != null ? condition.value().trim() : "";
            if (value.isBlank() || value.length() > MAX_CONDITION_VALUE_LENGTH) {
                continue;
            }
            if (PlanChangeConditions.KEYS.contains(key)
                    && (!PlanChangeConditions.valid(key, value)
                        || !value.equals(PlanChangeConditions.extract(reply, pendingKeys).get(key)))) continue;
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
        try {
            return runInTransaction(() -> saveAndBuildResult(userMessage, payload, method));
        } catch (DataIntegrityViolationException exception) {
            return runInTransaction(() -> queryRoutingRepository
                    .findByMessage_MessageId(userMessage.getMessageId())
                    .map(routing -> {
                        log.info("[라우팅] 동시 저장된 기존 결과를 사용합니다: messageId={}",
                                userMessage.getMessageId());
                        return buildExistingResponse(routing, userMessage.getMessageId());
                    })
                    .orElseThrow(() -> exception));
        }
    }

    private IntentRouteResponse saveAndBuildResult(
            ChatMessage message,
            LlmRoutingPayload payload,
            QueryRouting.Method method) {

        if (payload == null) {
            payload = ruleBasedFallback.classify(message != null ? message.getContent() : "");
        }

        QueryRouting routing = queryRoutingRepository.saveAndFlush(
                intentConverter.toQueryRouting(message, payload, method));

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
                : payload.intent() == QueryRouting.Intent.STORE
                    ? ConsultRequest.Intent.STORE : ConsultRequest.Intent.FAQ;

            short order = (short) (i + 1);
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
