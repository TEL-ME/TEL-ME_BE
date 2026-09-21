package com.telme.intent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.entity.ChatMessage;
import com.telme.consult.entity.ConsultCondition;
import com.telme.consult.entity.ConsultRequest;
import com.telme.consult.repository.ConsultRequestRepository;
import com.telme.global.common.exception.GeneralException;
import com.telme.intent.converter.IntentConverter;
import com.telme.intent.dto.res.IntentRouteResponse;
import com.telme.intent.dto.res.IntentRouteResponse.IntentSubQueryResponse;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final QueryRoutingRepository queryRoutingRepository;
    private final ConsultRequestRepository consultRequestRepository;
    private final RuleBasedRoutingFallback ruleBasedFallback;
    private final IntentConverter intentConverter;
    private final TransactionTemplate transactionTemplate;

    public QueryRoutingService(
            LlmClient llmClient,
            ObjectMapper objectMapper,
            QueryRoutingRepository queryRoutingRepository,
            ConsultRequestRepository consultRequestRepository,
            RuleBasedRoutingFallback ruleBasedFallback,
            IntentConverter intentConverter) {
        this(llmClient, objectMapper, queryRoutingRepository, consultRequestRepository, ruleBasedFallback, intentConverter, null);
    }

    @Autowired
    public QueryRoutingService(
            LlmClient llmClient,
            ObjectMapper objectMapper,
            QueryRoutingRepository queryRoutingRepository,
            ConsultRequestRepository consultRequestRepository,
            RuleBasedRoutingFallback ruleBasedFallback,
            IntentConverter intentConverter,
            @Autowired(required = false) TransactionTemplate transactionTemplate) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
        this.queryRoutingRepository = queryRoutingRepository;
        this.consultRequestRepository = consultRequestRepository;
        this.ruleBasedFallback = ruleBasedFallback;
        this.intentConverter = intentConverter;
        this.transactionTemplate = transactionTemplate;
    }

    public IntentRouteResponse route(ChatMessage userMessage) {
        if (userMessage == null) {
            throw new IllegalArgumentException("사용자 메시지는 필수입니다.");
        }

        if (userMessage.getMessageId() != null) {
            Optional<QueryRouting> existing = queryRoutingRepository.findByMessage_MessageId(userMessage.getMessageId());
            if (existing.isPresent()) {
                log.info("[라우팅] 이미 라우팅된 메시지입니다. 기존 결과를 반환합니다: messageId={}", userMessage.getMessageId());
                return runInTransaction(() -> buildExistingResponse(existing.get(), userMessage.getMessageId()));
            }
        }

        String question = userMessage.getContent() != null ? userMessage.getContent().trim() : "";

        if (question.isBlank()) {
            log.info("[라우팅] 질문 내용이 비어 있어 UNKNOWN으로 처리합니다.");
            LlmRoutingPayload fallbackPayload = ruleBasedFallback.classify(question);
            return executeInTransaction(userMessage, fallbackPayload, QueryRouting.Method.RULE);
        }

        LlmRoutingPayload payload;
        QueryRouting.Method method;

        try {
            LlmRequest request = LlmRequest.builder()
                .taskType(TaskType.ROUTING)
                .systemPrompt(RoutingPromptTemplates.ROUTING_SYSTEM_PROMPT)
                .userPrompt(question)
                .format(ResponseFormat.JSON)
                .temperature(0.1)
                .maxTokens(500)
                .build();

            String json = llmClient.generate(request);
            if (json == null || json.isBlank()) {
                throw new IllegalStateException("LLM 응답이 비어 있습니다.");
            }

            payload = objectMapper.readValue(json, LlmRoutingPayload.class);
            if (payload == null) {
                throw new IllegalStateException("LLM 응답 역직렬화 결과가 null입니다.");
            }

            method = QueryRouting.Method.LLM;

            log.info("[라우팅] LLM 분류 완료 -> intent={}, confidence={}",
                    payload.intent(), payload.confidence());

        } catch (GeneralException e) {
            if (e.getErrorCode() instanceof LlmErrorCode || e.getErrorCode() == IntentErrorCode.LLM_CONNECTION_FAILED) {
                log.warn("[라우팅] LLM 오류 발생, Rule Fallback으로 전환: {}", e.getMessage());
                payload = ruleBasedFallback.classify(question);
                method = QueryRouting.Method.RULE;
            } else {
                throw e;
            }
        } catch (RestClientException | JsonProcessingException | IllegalStateException e) {
            log.warn("[라우팅] LLM 호출 또는 파싱 실패, Rule Fallback으로 전환: {}", e.getMessage());
            payload = ruleBasedFallback.classify(question);
            method = QueryRouting.Method.RULE;
        }

        return executeInTransaction(userMessage, payload, method);
    }

    private <T> T runInTransaction(Supplier<T> action) {
        if (transactionTemplate != null) {
            return transactionTemplate.execute(status -> action.get());
        }
        return action.get();
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
