package com.telme.store.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// FE 검증 중 위치 오차·최신성과 호출량을 조정할 수 있게 한다. 변경은 재시작 후 적용된다.
@ConfigurationProperties(prefix = "store.route-cache")
public record StoreRouteCacheProperties(
        @DefaultValue("2m") Duration carTtl,
        @DefaultValue("5m") Duration transitTtl,
        @DefaultValue("10m") Duration walkTtl,
        @DefaultValue("10m") Duration bicycleTtl,
        @DefaultValue("3") int carTransitOriginScale,
        @DefaultValue("4") int walkBicycleOriginScale) {

    private static final Duration MIN_TTL = Duration.ofSeconds(1);
    private static final Duration MAX_TTL = Duration.ofHours(1);
    private static final int MIN_ORIGIN_SCALE = 3;
    private static final int MAX_ORIGIN_SCALE = 6;

    public StoreRouteCacheProperties {
        validateTtl(carTtl, "car-ttl");
        validateTtl(transitTtl, "transit-ttl");
        validateTtl(walkTtl, "walk-ttl");
        validateTtl(bicycleTtl, "bicycle-ttl");
        validateOriginScale(carTransitOriginScale, "car-transit-origin-scale");
        validateOriginScale(walkBicycleOriginScale, "walk-bicycle-origin-scale");
    }

    private static void validateTtl(Duration ttl, String name) {
        // 과도한 외부 호출이나 오래된 경로 재사용을 설정 실수로 유발하지 않도록 범위를 제한한다.
        if (ttl == null || ttl.compareTo(MIN_TTL) < 0 || ttl.compareTo(MAX_TTL) > 0) {
            throw new IllegalArgumentException("매장 경로 캐시 " + name + "은 1초 이상, 1시간 이하여야 합니다.");
        }
    }

    private static void validateOriginScale(int scale, String name) {
        if (scale < MIN_ORIGIN_SCALE || scale > MAX_ORIGIN_SCALE) {
            throw new IllegalArgumentException("매장 경로 캐시 " + name + "은 소수점 3~6자리여야 합니다.");
        }
    }
}
