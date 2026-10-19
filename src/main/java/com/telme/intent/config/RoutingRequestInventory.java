package com.telme.intent.config;

/** 요청 개수 판정을 할지 여부. 설정값이 없으면 라우팅 모델이 엑사원일 때만 한다. */
public record RoutingRequestInventory(boolean enabled) {
}
