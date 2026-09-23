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
    QueryRouting.Method method
) {
    public static final String LOCATION_KEY = "location";
    public static final String SERVICE_TYPE_KEY = "serviceType";

    public FollowUpRouteResponse {
        conditions = conditions != null ? Map.copyOf(conditions) : Collections.emptyMap();
        declinedKeys = declinedKeys != null ? Set.copyOf(declinedKeys) : Collections.emptySet();
    }

    public static FollowUpRouteResponse noTarget() {
        return new FollowUpRouteResponse(
            null, Collections.emptyMap(), Collections.emptySet(), QueryRouting.Method.RULE);
    }

    public boolean hasTarget() {
        return consultRequestId != null;
    }
}
