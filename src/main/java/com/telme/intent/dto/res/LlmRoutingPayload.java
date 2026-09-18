package com.telme.intent.dto.res;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.telme.consult.entity.ConsultRequest;
import com.telme.intent.entity.QueryRouting.Intent;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LlmRoutingPayload(
    Intent intent,
    BigDecimal confidence,
    String refinedQuery,
    Map<String, String> extractedConditions,
    List<SubQueryPayload> subQueries
) {
    public LlmRoutingPayload {
        if (intent == null) intent = Intent.UNKNOWN;
        if (extractedConditions == null) extractedConditions = Collections.emptyMap();
        if (subQueries == null) subQueries = Collections.emptyList();
        if (confidence == null) confidence = BigDecimal.ZERO;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SubQueryPayload(
        Short order,
        ConsultRequest.Intent intent,
        String queryText,
        Map<String, String> conditions
    ) {
        public SubQueryPayload {
            if (conditions == null) conditions = Collections.emptyMap();
        }
    }
}
