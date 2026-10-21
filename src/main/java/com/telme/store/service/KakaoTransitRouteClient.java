package com.telme.store.service;

import com.telme.global.common.exception.GeneralException;
import com.telme.store.config.KakaoRouteProperties;
import com.telme.store.converter.StoreRouteConverter;
import com.telme.store.dto.req.RouteMode;
import com.telme.store.dto.res.KakaoTransitRouteResponse;
import com.telme.store.dto.res.StoreRouteResponse;
import com.telme.store.dto.res.StoreRouteResponse.Coordinate;
import com.telme.store.exception.StoreErrorCode;
import java.math.BigDecimal;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

// 카카오맵 대중교통 경로. 경로를 못 찾은 이유는 HTTP 상태가 아니라 응답의 status로 온다
@Component
@RequiredArgsConstructor
@Slf4j
public class KakaoTransitRouteClient implements StoreRouteClient {

    private static final String TAG = "KakaoTransit";
    private static final String TRANSIT_PATH = "/v2/routing/publictraffic";
    private static final String STATUS_OK = "OK";
    private static final String STATUS_SAME_POINT = "EQUAL_POINTS";
    private static final double EARTH_RADIUS_METERS = 6_371_000;
    private static final double MIN_ORIGIN_WALK_METERS = 30;
    private static final Set<String> NOT_FOUND_STATUSES = Set.of("STARTNODES_NULL", "ENDNODES_NULL", "NO_RESULTS");

    private final RestClient kakaoRouteRestClient;
    private final StoreRouteConverter storeRouteConverter;
    private final KakaoWalkRouteClient kakaoWalkRouteClient;
    private final KakaoRouteProperties kakaoRouteProperties;

    @Override
    public RouteMode mode() {
        return RouteMode.TRANSIT;
    }

    // 경로는 카카오가 먼저 주는 것(추천 순)을 쓴다
    @Override
    public StoreRouteResponse findRoute(RouteEndpoints endpoints) {
        KakaoTransitRouteResponse response = KakaoRouteCalls.call(TAG, () -> kakaoRouteRestClient.get()
                .uri(uri -> uri.path(TRANSIT_PATH)
                        .queryParam("start_x", endpoints.originX())
                        .queryParam("start_y", endpoints.originY())
                        .queryParam("end_x", endpoints.destinationX())
                        .queryParam("end_y", endpoints.destinationY())
                        .build())
                .retrieve()
                .body(KakaoTransitRouteResponse.class));
        if (response == null || response.status() == null) {
            throw KakaoRouteCalls.createUnexpectedException(TAG, "status가 없음");
        }
        if (STATUS_SAME_POINT.equals(response.status())) {
            return StoreRouteResponse.arrived(RouteMode.TRANSIT);
        }
        if (NOT_FOUND_STATUSES.contains(response.status())) {
            throw new GeneralException(StoreErrorCode.ROUTE_NOT_FOUND);
        }
        if (!STATUS_OK.equals(response.status()) || response.routes() == null || response.routes().isEmpty()
                || response.routes().get(0).properties() == null) {
            throw KakaoRouteCalls.createUnexpectedException(TAG, "status=" + response.status());
        }
        StoreRouteResponse transit = storeRouteConverter.toTransitResponse(response.routes().get(0));
        return prependOriginWalk(endpoints, transit);
    }

    private StoreRouteResponse prependOriginWalk(RouteEndpoints endpoints, StoreRouteResponse transit) {
        if (!kakaoRouteProperties.transitOriginWalkEnabled() || transit.steps().isEmpty()
                || transit.steps().get(0).path().isEmpty()) {
            return transit;
        }
        // 첫 단계에 좌표가 없으면 뒤의 버스 구간을 골라 이미 포함된 도보를 중복 추가하지 않는다.
        Coordinate first = transit.steps().get(0).path().get(0);
        if (calculateOriginDistance(endpoints, first) <= MIN_ORIGIN_WALK_METERS) {
            return transit;
        }
        RouteEndpoints walkEndpoints = new RouteEndpoints(endpoints.originLatitude(), endpoints.originLongitude(),
                BigDecimal.valueOf(first.latitude()), BigDecimal.valueOf(first.longitude()));
        try {
            // 출발지는 상위 캐시에서 반올림한 값을 유지해야 캐시와 경로의 시작점이 일치한다.
            StoreRouteResponse walk = kakaoWalkRouteClient.findRoute(walkEndpoints);
            if (walk.steps().isEmpty()) {
                return transit;
            }
            return storeRouteConverter.prependOriginWalk(transit, walk);
        } catch (RuntimeException e) {
            // 보조 구간의 장애 때문에 이미 조회한 대중교통 경로까지 버리지 않는다. 위치는 기록하지 않는다.
            log.warn("[KakaoTransit] 출발지 도보 구간 생략 cause={}", e.getClass().getSimpleName());
            return transit;
        }
    }

    private double calculateOriginDistance(RouteEndpoints endpoints, Coordinate destination) {
        double originLatitude = Math.toRadians(endpoints.originLatitude().doubleValue());
        double destinationLatitude = Math.toRadians(destination.latitude());
        double latitudeDelta = destinationLatitude - originLatitude;
        double longitudeDelta = Math.toRadians(destination.longitude() - endpoints.originLongitude().doubleValue());
        double haversine = Math.pow(Math.sin(latitudeDelta / 2), 2)
                + Math.cos(originLatitude) * Math.cos(destinationLatitude)
                * Math.pow(Math.sin(longitudeDelta / 2), 2);
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.sqrt(Math.min(1, haversine)));
    }
}
