package com.telme.store.service;

import java.math.BigDecimal;

// 길찾기 출발지·도착지. 카카오는 x(경도), y(위도) 순서로 받으므로 문자열 변환을 여기서 한 번만 한다
public record RouteEndpoints(
        BigDecimal originLatitude,
        BigDecimal originLongitude,
        BigDecimal destinationLatitude,
        BigDecimal destinationLongitude) {

    String originX() {
        return originLongitude.toPlainString();
    }

    String originY() {
        return originLatitude.toPlainString();
    }

    String destinationX() {
        return destinationLongitude.toPlainString();
    }

    String destinationY() {
        return destinationLatitude.toPlainString();
    }
}
