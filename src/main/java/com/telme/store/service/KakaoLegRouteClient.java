package com.telme.store.service;

import com.telme.global.common.exception.GeneralException;
import com.telme.store.converter.StoreRouteConverter;
import com.telme.store.dto.req.RouteMode;
import com.telme.store.dto.res.KakaoLegRouteResponse;
import com.telme.store.dto.res.StoreRouteResponse;
import com.telme.store.exception.StoreErrorCode;
import java.util.Set;
import org.springframework.web.client.RestClient;

// 카카오맵 도보·자전거 경로. 두 API의 요청·응답이 같고 경로만 달라 하나로 묶었다.
// 경로를 못 찾은 이유는 HTTP 상태가 아니라 응답의 status로 온다
abstract class KakaoLegRouteClient implements StoreRouteClient {

    private static final String STATUS_OK = "OK";
    private static final String STATUS_SAME_POINT = "SAME_POINT";
    // TOO_FAR_AWAY: 출발지와 도착지가 너무 멀리 떨어져 있음
    private static final Set<String> NOT_FOUND_STATUSES = Set.of(
            "START_LINK_NOT_FOUND", "END_LINK_NOT_FOUND", "TOO_MANY_SEARCH_LINK", "TOO_FAR_AWAY",
            "ROUTE_RESULT_NOT_FOUND");

    private final KakaoRouteCalls kakaoRouteCalls;
    private final RestClient kakaoRouteRestClient;
    private final StoreRouteConverter storeRouteConverter;
    private final RouteMode mode;
    private final String path;
    private final String tag;

    KakaoLegRouteClient(RestClient kakaoRouteRestClient, StoreRouteConverter storeRouteConverter,
                        RouteMode mode, String path, String tag, KakaoRouteCalls kakaoRouteCalls) {
        this.kakaoRouteCalls = kakaoRouteCalls;
        this.kakaoRouteRestClient = kakaoRouteRestClient;
        this.storeRouteConverter = storeRouteConverter;
        this.mode = mode;
        this.path = path;
        this.tag = tag;
    }

    @Override
    public RouteMode mode() {
        return mode;
    }

    @Override
    public StoreRouteResponse findRoute(RouteEndpoints endpoints) {
        KakaoLegRouteResponse response = kakaoRouteCalls.call(mode, tag, () -> kakaoRouteRestClient.get()
                .uri(uri -> uri.path(path)
                        .queryParam("start_x", endpoints.originX())
                        .queryParam("start_y", endpoints.originY())
                        .queryParam("end_x", endpoints.destinationX())
                        .queryParam("end_y", endpoints.destinationY())
                        .build())
                .retrieve()
                .body(KakaoLegRouteResponse.class));
        if (response == null || response.status() == null) {
            throw KakaoRouteCalls.createUnexpectedException(tag, "status가 없음");
        }
        if (STATUS_SAME_POINT.equals(response.status())) {
            return StoreRouteResponse.arrived(mode);
        }
        if (NOT_FOUND_STATUSES.contains(response.status())) {
            throw new GeneralException(StoreErrorCode.ROUTE_NOT_FOUND);
        }
        if (!STATUS_OK.equals(response.status()) || response.route() == null
                || response.route().properties() == null) {
            throw KakaoRouteCalls.createUnexpectedException(tag, "status=" + response.status());
        }
        return storeRouteConverter.toLegResponse(mode, response.route());
    }
}
