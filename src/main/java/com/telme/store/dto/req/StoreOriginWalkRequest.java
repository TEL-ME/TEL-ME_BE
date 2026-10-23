package com.telme.store.dto.req;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

// 선택한 대중교통 경로의 첫 좌표까지 도보를 조회한다. 두 지점 모두 국내 좌표만 받는다.
@Builder
public record StoreOriginWalkRequest(
        @NotNull
        @DecimalMin("33")
        @DecimalMax("39")
        Double originLatitude,
        @NotNull
        @DecimalMin("124")
        @DecimalMax("132")
        Double originLongitude,
        @NotNull
        @DecimalMin("33")
        @DecimalMax("39")
        Double destinationLatitude,
        @NotNull
        @DecimalMin("124")
        @DecimalMax("132")
        Double destinationLongitude
) {
}
