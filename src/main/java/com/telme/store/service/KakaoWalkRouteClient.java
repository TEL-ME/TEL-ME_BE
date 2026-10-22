package com.telme.store.service;

import com.telme.store.converter.StoreRouteConverter;
import com.telme.store.dto.req.RouteMode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class KakaoWalkRouteClient extends KakaoLegRouteClient {

    public KakaoWalkRouteClient(RestClient kakaoRouteRestClient, StoreRouteConverter storeRouteConverter,
                                 KakaoRouteCalls kakaoRouteCalls) {
        super(kakaoRouteRestClient, storeRouteConverter, RouteMode.WALK, "/v2/routing/walk",
                "KakaoWalk", kakaoRouteCalls);
    }
}
