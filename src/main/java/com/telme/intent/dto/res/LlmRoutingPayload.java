package com.telme.intent.dto.res;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.telme.intent.entity.QueryRouting.Intent;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Ollama LLM JSON 응답을 역직렬화하기 위한 내부 응답 페이로드 DTO.
 *
 * 팀 코딩 컨벤션 규칙 준수:
 *   - 위치: {domain}/dto/res
 *   - Java record 사용
 *   - Record Compact Constructor를 통해 null 방어 (NPE 원천 차단)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LlmRoutingPayload(
    Intent intent,
    BigDecimal confidence,
    String refinedQuery,
    Map<String, String> extractedConditions,
    List<SubQueryPayload> subQueries
) {
    public LlmRoutingPayload {
        if (extractedConditions == null) extractedConditions = Collections.emptyMap();
        if (subQueries == null) subQueries = Collections.emptyList();
        if (confidence == null) confidence = BigDecimal.ZERO;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SubQueryPayload(
        Short order,
        Intent intent,
        String queryText,
        Map<String, String> conditions
    ) {
        public SubQueryPayload {
            if (conditions == null) conditions = Collections.emptyMap();
        }
    }
}
