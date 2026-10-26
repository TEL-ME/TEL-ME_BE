package com.telme.store.config;

import com.telme.store.dto.req.RouteMode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// 공급자의 쿼터 자체가 아니라 이 서버의 길찾기에 배정한 호출 예산이다. 0은 해당 수단 차단이다.
@ConfigurationProperties(prefix = "store.route")
public record StoreRouteUsageProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("9000") long carDailyLimit,
        @DefaultValue("900") long transitDailyLimit,
        @DefaultValue("900") long walkDailyLimit,
        @DefaultValue("900") long bicycleDailyLimit) {

    public StoreRouteUsageProperties {
        if (carDailyLimit < 0 || transitDailyLimit < 0 || walkDailyLimit < 0 || bicycleDailyLimit < 0) {
            throw new IllegalArgumentException("길찾기 API별 일일 호출 예산은 0 이상이어야 합니다.");
        }
    }

    public long dailyLimit(RouteMode mode) {
        return switch (mode) {
            case CAR -> carDailyLimit;
            case TRANSIT -> transitDailyLimit;
            case WALK -> walkDailyLimit;
            case BICYCLE -> bicycleDailyLimit;
        };
    }
}
