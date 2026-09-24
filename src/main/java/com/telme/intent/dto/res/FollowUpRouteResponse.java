package com.telme.intent.dto.res;

import com.telme.intent.entity.QueryRouting;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

// 거절은 값을 못 찾은 경우와 구분해야 해서 conditions에 섞지 않고 declinedKeys로 분리한다
public record FollowUpRouteResponse(
    Long consultRequestId,
    Map<String, String> conditions,
    Set<String> declinedKeys,
    QueryRouting.Method method,
    Disposition disposition
) {
    public static final String LOCATION_KEY = "location";
    public static final String SERVICE_TYPE_KEY = "serviceType";

    public enum Disposition {
        CONDITION_RESPONSE,
        DEFERRED,
        NEW_QUESTION,
        NO_TARGET
    }

    public FollowUpRouteResponse(
        Long consultRequestId,
        Map<String, String> conditions,
        Set<String> declinedKeys,
        QueryRouting.Method method
    ) {
        this(
            consultRequestId,
            conditions,
            declinedKeys,
            method,
            consultRequestId == null
                ? Disposition.NO_TARGET
                : hasConditionAnswer(conditions, declinedKeys)
                    ? Disposition.CONDITION_RESPONSE
                    : Disposition.DEFERRED
        );
    }

    public FollowUpRouteResponse {
        conditions = conditions != null ? Map.copyOf(conditions) : Collections.emptyMap();
        declinedKeys = declinedKeys != null ? Set.copyOf(declinedKeys) : Collections.emptySet();
        disposition = disposition != null ? disposition : Disposition.DEFERRED;
    }

    public static FollowUpRouteResponse noTarget() {
        return new FollowUpRouteResponse(
            null,
            Collections.emptyMap(),
            Collections.emptySet(),
            QueryRouting.Method.RULE,
            Disposition.NO_TARGET);
    }

    public boolean hasTarget() {
        return consultRequestId != null;
    }

    private static boolean hasConditionAnswer(
        Map<String, String> conditions,
        Set<String> declinedKeys
    ) {
        return conditions != null && !conditions.isEmpty()
            || declinedKeys != null && !declinedKeys.isEmpty();
    }
}
