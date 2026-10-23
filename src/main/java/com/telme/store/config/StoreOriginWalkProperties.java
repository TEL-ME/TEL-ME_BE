package com.telme.store.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.security.web.util.matcher.IpAddressMatcher;

@ConfigurationProperties(prefix = "store.route.origin-walk")
public record StoreOriginWalkProperties(
        @DefaultValue("3000") Integer maxStraightDistanceMeters,
        @DefaultValue("30") Integer requestsPerMinute,
        List<String> trustedProxies) {

    public StoreOriginWalkProperties {
        maxStraightDistanceMeters = maxStraightDistanceMeters == null ? 3000 : maxStraightDistanceMeters;
        requestsPerMinute = requestsPerMinute == null ? 30 : requestsPerMinute;
        if (maxStraightDistanceMeters <= 0 || requestsPerMinute <= 0) {
            throw new IllegalArgumentException("출발 도보 거리 상한과 분당 요청 수는 0보다 커야 합니다.");
        }
        trustedProxies = trustedProxies == null ? List.of() : trustedProxies.stream()
                .map(String::trim).filter(value -> !value.isEmpty()).toList();
        trustedProxies.forEach(IpAddressMatcher::new);
    }
}
