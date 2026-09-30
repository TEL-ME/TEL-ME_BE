package com.telme.store.dto.req;

import com.telme.store.entity.StoreServiceType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record StoreRegionSearchRequest(
        @Schema(description = "법정동코드 앞자리 (시도 2자리, 시군구 5자리 등)", example = "11680",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "지역 코드를 입력해 주세요.")
        @Pattern(regexp = "\\d{2}|\\d{5}|\\d{8}|\\d{10}",
                message = "지역 코드는 시도 2자리, 시군구 5자리, 읍면동 8자리, 리 10자리 숫자여야 합니다.")
        String region,

        @Schema(description = "가능 업무. 비우면 전체", implementation = StoreServiceType.Code.class)
        @Pattern(regexp = StoreRegionSearchRequest.SERVICE_TYPE_PATTERN, message = "허용되지 않는 업무 코드입니다.")
        String serviceType,

        @Schema(description = "페이지 번호 (0부터)", defaultValue = "0")
        @Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다.")
        Integer page,

        @Schema(description = "페이지 크기 (1~50)", defaultValue = "20")
        @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
        @Max(value = 50, message = "페이지 크기는 50 이하여야 합니다.")
        Integer size
) {
    public static final String SERVICE_TYPE_PATTERN = "NEW_LINE|PORT_IN|NAME_CHANGE|USIM_REISSUE";

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    public StoreRegionSearchRequest {
        if (serviceType != null && serviceType.isBlank()) {
            serviceType = null;
        }
        if (page == null) {
            page = DEFAULT_PAGE;
        }
        if (size == null) {
            size = DEFAULT_SIZE;
        }
    }

    public StoreServiceType.Code serviceTypeCode() {
        return serviceType == null ? null : StoreServiceType.Code.valueOf(serviceType);
    }
}
