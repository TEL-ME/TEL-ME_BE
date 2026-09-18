package com.telme.intent.dto.res;

import com.telme.consult.entity.ConsultRequest;
import com.telme.intent.entity.QueryRouting.Intent;
import com.telme.intent.entity.QueryRouting.Method;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record IntentRouteResponse(
    Long routingId,
    Long messageId,
    Intent intent,
    String refinedQuery,
    BigDecimal confidence,
    Method method,
    Map<String, String> extractedConditions,
    List<IntentSubQueryResponse> subQueries
) {
    public record IntentSubQueryResponse(
        Long consultRequestId,
        Short order,
        ConsultRequest.Intent intent,
        String queryText,
        Map<String, String> conditions
    ) {}
}
