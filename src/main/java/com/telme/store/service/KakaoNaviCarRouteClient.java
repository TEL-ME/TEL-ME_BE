package com.telme.store.service;

import com.telme.global.common.exception.GeneralException;
import com.telme.store.converter.StoreRouteConverter;
import com.telme.store.dto.req.RouteMode;
import com.telme.store.dto.res.KakaoNaviDirectionsResponse;
import com.telme.store.dto.res.StoreRouteResponse;
import com.telme.store.exception.StoreErrorCode;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

// 카카오모빌리티 자동차 길찾기. 경로를 못 찾은 이유는 HTTP 상태가 아니라 routes[0].result_code로 온다
@Component
@RequiredArgsConstructor
public class KakaoNaviCarRouteClient implements StoreRouteClient {

    private static final String TAG = "KakaoNavi";
    private static final String DIRECTIONS_PATH = "/v1/directions";
    private static final int RESULT_SUCCESS = 0;
    // 출발지와 도착지가 5m 이내
    private static final int RESULT_SAME_POINT = 104;
    // 1: 결과 없음, 101~103: 경유지·출발지·도착지 주변 도로 없음, 105~107: 출발지·도착지·경유지 주변 통행 장애
    private static final Set<Integer> NOT_FOUND_RESULTS = Set.of(1, 101, 102, 103, 105, 106, 107);

    private final RestClient kakaoNaviRestClient;
    private final StoreRouteConverter storeRouteConverter;
    private final KakaoRouteCalls kakaoRouteCalls;

    @Override
    public RouteMode mode() {
        return RouteMode.CAR;
    }

    @Override
    public StoreRouteResponse findRoute(RouteEndpoints endpoints) {
        KakaoNaviDirectionsResponse response = kakaoRouteCalls.call(RouteMode.CAR, TAG, () -> kakaoNaviRestClient.get()
                .uri(uri -> uri.path(DIRECTIONS_PATH)
                        .queryParam("origin", endpoints.originX() + "," + endpoints.originY())
                        .queryParam("destination", endpoints.destinationX() + "," + endpoints.destinationY())
                        .build())
                .retrieve()
                .body(KakaoNaviDirectionsResponse.class));
        if (response == null || response.routes() == null || response.routes().isEmpty()) {
            throw KakaoRouteCalls.createUnexpectedException(TAG, "routes가 비어 있음");
        }
        KakaoNaviDirectionsResponse.Route route = response.routes().get(0);
        if (route == null) {
            throw KakaoRouteCalls.createUnexpectedException(TAG, "routes[0]가 null");
        }
        Integer resultCode = route.resultCode();
        if (resultCode != null && resultCode == RESULT_SAME_POINT) {
            return StoreRouteResponse.arrived(RouteMode.CAR);
        }
        if (resultCode != null && NOT_FOUND_RESULTS.contains(resultCode)) {
            throw new GeneralException(StoreErrorCode.ROUTE_NOT_FOUND);
        }
        if (resultCode == null || resultCode != RESULT_SUCCESS || route.summary() == null) {
            throw KakaoRouteCalls.createUnexpectedException(TAG, "result_code=" + resultCode);
        }
        return storeRouteConverter.toCarResponse(route);
    }
}
