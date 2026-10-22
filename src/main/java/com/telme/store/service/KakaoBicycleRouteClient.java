package com.telme.store.service;

import com.telme.store.converter.StoreRouteConverter;
import com.telme.store.dto.req.RouteMode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class KakaoBicycleRouteClient extends KakaoLegRouteClient {

    public KakaoBicycleRouteClient(RestClient kakaoRouteRestClient, StoreRouteConverter storeRouteConverter,
                                 KakaoRouteCalls kakaoRouteCalls) {
        super(kakaoRouteRestClient, storeRouteConverter, RouteMode.BICYCLE, "/v2/routing/bicycle",
                "KakaoBicycle", kakaoRouteCalls);
    }
}
