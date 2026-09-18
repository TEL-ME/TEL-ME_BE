package com.telme.intent.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.entity.ChatMessage;
import com.telme.consult.entity.ConsultCondition;
import com.telme.consult.entity.ConsultRequest;
import com.telme.intent.dto.res.IntentRouteResponse;
import com.telme.intent.dto.res.IntentRouteResponse.IntentSubQueryResponse;
import com.telme.intent.dto.res.LlmRoutingPayload;
import com.telme.intent.entity.QueryRouting;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class IntentConverter {

    private final ObjectMapper objectMapper;

    public QueryRouting toQueryRouting(
            ChatMessage message,
            LlmRoutingPayload payload,
            QueryRouting.Method method) {

        return QueryRouting.builder()
            .message(message)
            .intent(payload.intent())
            .refinedQuery(payload.refinedQuery())
            .extractedConditions(toJson(payload.extractedConditions()))
            .confidence(payload.confidence())
            .method(method)
            .build();
    }

    public ConsultRequest toConsultRequest(
            ChatMessage message,
            Short order,
            ConsultRequest.Intent intent,
            String queryText) {

        return ConsultRequest.builder()
            .session(message.getSession())
            .originMessage(message)
            .subqueryOrder(order)
            .intent(intent)
            .queryText(queryText)
            .build();
    }

    public ConsultCondition toConsultCondition(
            ConsultRequest consultRequest,
            String conditionKey,
            String conditionValue) {

        ConsultCondition.Status status = (conditionValue != null && !conditionValue.isBlank())
            ? ConsultCondition.Status.FILLED
            : ConsultCondition.Status.PENDING;

        return ConsultCondition.builder()
            .consultRequest(consultRequest)
            .conditionKey(conditionKey)
            .conditionValue(conditionValue)
            .source(ConsultCondition.Source.EXTRACTED)
            .status(status)
            .build();
    }

    public IntentSubQueryResponse toSubQueryResponse(
            ConsultRequest consultRequest,
            Short order,
            ConsultRequest.Intent intent,
            String queryText,
            Map<String, String> conditions) {

        return new IntentSubQueryResponse(
            consultRequest.getConsultRequestId(),
            order,
            intent,
            queryText,
            conditions
        );
    }

    public IntentRouteResponse toIntentRouteResponse(
            QueryRouting routing,
            Map<String, String> extractedConditions,
            List<IntentSubQueryResponse> subQueries) {

        return new IntentRouteResponse(
            routing.getRoutingId(),
            routing.getMessage().getMessageId(),
            routing.getIntent(),
            routing.getRefinedQuery(),
            routing.getConfidence(),
            routing.getMethod(),
            extractedConditions,
            subQueries
        );
    }

    public String toJson(Map<String, String> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            log.error("[IntentConverter] conditions -> JSON 직렬화 실패", e);
            return null;
        }
    }

    public Map<String, String> parseConditions(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.error("[IntentConverter] JSON -> conditions 역직렬화 실패", e);
            throw new IllegalArgumentException("조건 JSON 역직렬화 실패: " + json, e);
        }
    }
}
