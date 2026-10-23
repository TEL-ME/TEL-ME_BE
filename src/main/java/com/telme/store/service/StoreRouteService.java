package com.telme.store.service;

import com.telme.global.common.exception.GeneralException;
import com.telme.store.config.StoreRouteProperties;
import com.telme.store.dto.req.RouteMode;
import com.telme.store.dto.req.StoreOriginWalkRequest;
import com.telme.store.dto.req.StoreRouteRequest;
import com.telme.store.dto.res.StoreRouteResponse;
import com.telme.store.entity.Store;
import com.telme.store.exception.StoreErrorCode;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

// 사용자 출발지에서 매장까지의 경로를 이동 수단에 맞는 클라이언트로 조회한다.
// 외부 API를 기다리는 동안 DB 연결을 잡고 있지 않으려고 이 클래스에는 @Transactional을 걸지 않는다.
// 매장 조회는 트랜잭션이 걸린 StoreQueryService가 끝내고 돌려준다
// 카카오맵의 임시 캐싱 금지 정책에 따라 경로 응답은 저장·재사용하지 않는다.
@Service
public class StoreRouteService {

    private final StoreRouteUsageGuard usageGuard;
    private final StoreQueryService storeQueryService;
    private final StoreRouteProperties storeRouteProperties;
    private final Map<RouteMode, StoreRouteClient> clients = new EnumMap<>(RouteMode.class);

    public StoreRouteService(StoreQueryService storeQueryService,
                             List<StoreRouteClient> routeClients, StoreRouteProperties storeRouteProperties,
                             StoreRouteUsageGuard usageGuard) {
        this.usageGuard = usageGuard;
        this.storeQueryService = storeQueryService;
        this.storeRouteProperties = storeRouteProperties;
        routeClients.forEach(client -> clients.put(client.mode(), client));
        for (RouteMode mode : RouteMode.values()) {
            if (!clients.containsKey(mode)) {
                throw new IllegalStateException("길찾기 클라이언트가 없는 이동 수단입니다: " + mode);
            }
        }
    }

    public StoreRouteResponse getRoute(long storeId, StoreRouteRequest request) {
        usageGuard.checkEnabled();
        Store store = storeQueryService.getOpenStore(storeId);
        RouteMode mode = request.mode();
        RouteEndpoints endpoints = new RouteEndpoints(
                BigDecimal.valueOf(request.originLatitude()),
                BigDecimal.valueOf(request.originLongitude()),
                store.getLatitude(),
                store.getLongitude());
        return loadRoute(mode, endpoints);
    }

    public StoreRouteResponse getOriginWalk(long storeId, StoreOriginWalkRequest request) {
        usageGuard.checkEnabled();
        // 폐점·없는 매장에 대해 외부 호출 예산을 쓰지 않는다. 외부 호출 중 DB 연결은 유지하지 않는다.
        storeQueryService.getOpenStore(storeId);
        RouteEndpoints endpoints = new RouteEndpoints(
                BigDecimal.valueOf(request.originLatitude()),
                BigDecimal.valueOf(request.originLongitude()),
                BigDecimal.valueOf(request.destinationLatitude()),
                BigDecimal.valueOf(request.destinationLongitude()));
        return loadRoute(RouteMode.WALK, endpoints);
    }

    private StoreRouteResponse loadRoute(RouteMode mode, RouteEndpoints endpoints) {
        StoreRouteResponse response = clients.get(mode).findRoute(endpoints);
        int maxDistance = storeRouteProperties.maxDistanceMeters();
        // 실제 경로 길이는 카카오 조회 후에 알 수 있으므로 외부 호출 쿼터는 사용한다.
        if (maxDistance > 0 && response.distanceMeters() > maxDistance) {
            throw new GeneralException(StoreErrorCode.ROUTE_DISTANCE_EXCEEDED);
        }
        // 첫 경로가 거리 안이어도 더 돌아가는 대체 경로가 한도를 넘을 수 있어 같은 기준으로 뺀다
        if (maxDistance > 0 && response.alternatives().stream().anyMatch(route -> route.distanceMeters() > maxDistance)) {
            return response.withAlternatives(response.alternatives().stream()
                    .filter(route -> route.distanceMeters() <= maxDistance)
                    .toList());
        }
        return response;
    }
}
