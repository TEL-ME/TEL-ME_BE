package com.telme.store.service;

import com.telme.store.config.StoreRouteCacheProperties;
import com.telme.store.dto.req.RouteMode;
import com.telme.store.dto.res.StoreRouteResponse;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

// 카카오 무료 호출 한도를 아끼려고 같은 매장·수단·(반올림한) 출발지의 경로는 잠시 다시 부르지 않는다. 호출 실패(예외)는 저장하지 않는다.
// 수단마다 변하는 속도가 달라 저장 시간이 다르다: 자동차는 교통 상황, 대중교통은 배차, 도보·자전거는 거의 변하지 않는다
@Component
public class StoreRouteCache {

    // 자동차 경로는 좌표가 수천 개라 한 건이 수백 KB일 수 있어 로컬 검색 캐시보다 작게 잡는다
    private static final int MAX_SIZE = 300;
    private final StoreRouteCacheProperties properties;
    private final Map<RouteMode, ExpiringLruCache<StoreRouteResponse>> caches = new EnumMap<>(RouteMode.class);

    public StoreRouteCache(Clock clock, StoreRouteCacheProperties properties) {
        this.properties = properties;
        caches.put(RouteMode.CAR, new ExpiringLruCache<>(clock, properties.carTtl(), MAX_SIZE));
        caches.put(RouteMode.TRANSIT, new ExpiringLruCache<>(clock, properties.transitTtl(), MAX_SIZE));
        caches.put(RouteMode.WALK, new ExpiringLruCache<>(clock, properties.walkTtl(), MAX_SIZE));
        caches.put(RouteMode.BICYCLE, new ExpiringLruCache<>(clock, properties.bicycleTtl(), MAX_SIZE));
    }

    // 캐시 키와 카카오에 보내는 좌표를 같은 값으로 맞추려고 출발지를 먼저 반올림해 쓴다.
    // 반올림하지 않은 좌표로 부르면 다른 사용자가 이전 사용자의 정확한 위치에서 출발한 경로를 받게 된다
    public RouteEndpoints roundOrigin(RouteMode mode, RouteEndpoints endpoints) {
        int scale = switch (mode) {
            case CAR, TRANSIT -> properties.carTransitOriginScale();
            case WALK, BICYCLE -> properties.walkBicycleOriginScale();
        };
        return new RouteEndpoints(
                endpoints.originLatitude().setScale(scale, RoundingMode.HALF_UP),
                endpoints.originLongitude().setScale(scale, RoundingMode.HALF_UP),
                endpoints.destinationLatitude(),
                endpoints.destinationLongitude());
    }

    public StoreRouteResponse getOrLoad(RouteMode mode, RouteEndpoints endpoints,
                                        Supplier<StoreRouteResponse> loader) {
        String key = endpoints.originY() + "," + endpoints.originX() + ">"
                + endpoints.destinationY() + "," + endpoints.destinationX();
        return caches.get(mode).getOrLoad(key, loader);
    }
}
