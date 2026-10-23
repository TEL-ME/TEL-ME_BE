package com.telme.store.dto.req;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

// 선택한 대중교통 경로의 첫 좌표까지 도보를 조회한다. 두 지점 모두 국내 좌표만 받는다.
@Builder
public record StoreOriginWalkRequest(
        @NotNull(message = "출발지 위도를 입력해 주세요.")
        @DecimalMin(value = "33", message = "출발지 위도는 대한민국 안의 값이어야 합니다.")
        @DecimalMax(value = "39", message = "출발지 위도는 대한민국 안의 값이어야 합니다.")
        Double originLatitude,
        @NotNull(message = "출발지 경도를 입력해 주세요.")
        @DecimalMin(value = "124", message = "출발지 경도는 대한민국 안의 값이어야 합니다.")
        @DecimalMax(value = "132", message = "출발지 경도는 대한민국 안의 값이어야 합니다.")
        Double originLongitude,
        @NotNull(message = "목적지 위도를 입력해 주세요.")
        @DecimalMin(value = "33", message = "목적지 위도는 대한민국 안의 값이어야 합니다.")
        @DecimalMax(value = "39", message = "목적지 위도는 대한민국 안의 값이어야 합니다.")
        Double destinationLatitude,
        @NotNull(message = "목적지 경도를 입력해 주세요.")
        @DecimalMin(value = "124", message = "목적지 경도는 대한민국 안의 값이어야 합니다.")
        @DecimalMax(value = "132", message = "목적지 경도는 대한민국 안의 값이어야 합니다.")
        Double destinationLongitude
) {
}
