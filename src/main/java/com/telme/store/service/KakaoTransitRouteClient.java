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
import java.util.ArrayList;
import java.util.List;
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
    private final KakaoRouteCalls kakaoRouteCalls;

    @Override
    public RouteMode mode() {
        return RouteMode.TRANSIT;
    }

    // 카카오가 추천 순으로 주는 경로 중 첫 경로를 응답의 값으로, 나머지를 alternatives로 내린다. 카카오 호출은 한 번이다
    @Override
    public StoreRouteResponse findRoute(RouteEndpoints endpoints) {
        KakaoTransitRouteResponse response = kakaoRouteCalls.call(RouteMode.TRANSIT, TAG,
                () -> kakaoRouteRestClient.get()
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
                || response.routes().get(0) == null || response.routes().get(0).properties() == null) {
            throw KakaoRouteCalls.createUnexpectedException(TAG, "status=" + response.status());
        }
        StoreRouteResponse transit = storeRouteConverter.toTransitResponse(response.routes().get(0));
        return prependOriginWalk(endpoints, transit)
                .withAlternatives(toAlternatives(response.routes().subList(1, response.routes().size())));
    }

    // 첫 경로는 해석하지 못하면 실패로 알리지만, 나머지 경로는 해석하지 못한 것만 빼고 내린다
    private List<StoreRouteResponse> toAlternatives(List<KakaoTransitRouteResponse.Route> routes) {
        List<StoreRouteResponse> alternatives = new ArrayList<>();
        for (int i = 0; i < routes.size(); i++) {
            try {
                alternatives.add(storeRouteConverter.toTransitResponse(routes.get(i)));
            } catch (GeneralException e) {
                log.warn("[KakaoTransit] 대체 경로 생략 index={} code={}", i + 1, e.getErrorCode().getCode());
            }
        }
        return alternatives;
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
