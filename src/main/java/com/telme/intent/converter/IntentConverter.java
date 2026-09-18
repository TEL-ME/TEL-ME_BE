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

/**
 * 의도 라우팅(Intent) 도메인의 Entity ↔ DTO 변환 컴포넌트.
 *
 * 팀 코딩 컨벤션 규칙 준수:
 *   - 위치: {domain}/converter/{Domain}Converter.java
 *   - @Component 등록
 *   - Service가 주입받아 엔티티-DTO 매핑을 전담
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IntentConverter {

    private final ObjectMapper objectMapper;

    /**
     * LlmRoutingPayload와 원본 메시지로부터 QueryRouting 엔티티 생성.
     */
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

    /**
     * 분해된 서브질의 정보로부터 ConsultRequest 엔티티 생성.
     */
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

    /**
     * 서브질의 추출 조건으로부터 ConsultCondition 엔티티 생성.
     */
    public ConsultCondition toConsultCondition(
            ConsultRequest consultRequest,
            String conditionKey,
            String conditionValue) {

        return ConsultCondition.builder()
            .consultRequest(consultRequest)
            .conditionKey(conditionKey)
            .conditionValue(conditionValue)
            .source(ConsultCondition.Source.EXTRACTED)
            .build();
    }

    /**
     * ConsultRequest 엔티티와 조건 Map으로부터 SubQuery 응답 DTO 생성.
     */
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

    /**
     * 저장된 QueryRouting 엔티티와 서브질의 목록으로부터 최종 응답 DTO 생성.
     */
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

    /**
     * Map -> JSONB 문자열 직렬화.
     */
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

    /**
     * JSONB 문자열 -> Map 역직렬화.
     */
    public Map<String, String> parseConditions(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.error("[IntentConverter] JSON -> conditions 역직렬화 실패", e);
            return Collections.emptyMap();
        }
    }
}
