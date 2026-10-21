package com.telme.store.dto.req;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

// 좌표 범위는 대한민국 전역(제주·독도·백령도 포함)만 허용한다. 엉뚱한 좌표로 유료 호출 한도를 쓰는 것을 막는다
@Builder
public record StoreRouteRequest(
        @NotNull(message = "이동 수단을 입력해 주세요.")
        RouteMode mode,
        @NotNull(message = "출발지 위도를 입력해 주세요.")
        @DecimalMin(value = "33", message = "출발지 위도는 대한민국 안의 값이어야 합니다.")
        @DecimalMax(value = "39", message = "출발지 위도는 대한민국 안의 값이어야 합니다.")
        Double originLatitude,
        @NotNull(message = "출발지 경도를 입력해 주세요.")
        @DecimalMin(value = "124", message = "출발지 경도는 대한민국 안의 값이어야 합니다.")
        @DecimalMax(value = "132", message = "출발지 경도는 대한민국 안의 값이어야 합니다.")
        Double originLongitude
) {
}
