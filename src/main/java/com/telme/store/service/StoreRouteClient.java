package com.telme.store.service;

import com.telme.store.dto.req.RouteMode;
import com.telme.store.dto.res.StoreRouteResponse;

// 이동 수단 하나의 경로를 조회한다. 공급자(카카오·T맵 등)를 바꿔도 구현체만 교체하면 되도록 서비스가 이 인터페이스만 안다
public interface StoreRouteClient {

    RouteMode mode();

    StoreRouteResponse findRoute(RouteEndpoints endpoints);
}
