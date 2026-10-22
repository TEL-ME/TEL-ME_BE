package com.telme.store.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// 경로 길이 제한은 선택 정책이다. 설정하지 않은 환경의 기존 동작을 유지한다.
@ConfigurationProperties(prefix = "store.route")
public record StoreRouteProperties(@DefaultValue("0") Integer maxDistanceMeters) {

    public StoreRouteProperties {
        // 빈 환경변수도 미사용으로 취급한다. 바인딩된 null은 내부에서 0으로 정규화한다.
        if (maxDistanceMeters == null) {
            maxDistanceMeters = 0;
        }
        if (maxDistanceMeters < 0) {
            throw new IllegalArgumentException("매장 경로 최대 거리는 0(제한 없음) 이상이어야 합니다.");
        }
    }
}
