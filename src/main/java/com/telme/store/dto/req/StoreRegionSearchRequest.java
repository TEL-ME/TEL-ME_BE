package com.telme.store.dto.req;

import com.telme.global.common.validation.EnumValid;
import com.telme.store.entity.StoreServiceType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record StoreRegionSearchRequest(
        @Schema(description = "법정동코드 앞자리 (시도 2자리, 시군구 5자리 등)", example = "11680",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "{store.region.required}")
        @Pattern(regexp = "\\d{2}|\\d{4}|\\d{5}|\\d{8}|\\d{10}",
                message = "{store.region.pattern}")
        String region,

        @Schema(description = "가능 업무. 비우면 전체", implementation = StoreServiceType.Code.class)
        @EnumValid(enumClass = StoreServiceType.Code.class, message = "{store.service.invalid}")
        String serviceType,

        @Schema(description = "페이지 번호 (0부터)", defaultValue = "0")
        @Min(value = 0, message = "{store.page.min}")
        Integer page,

        @Schema(description = "페이지 크기 (1~50)", defaultValue = "20")
        @Min(value = 1, message = "{store.size.min}")
        @Max(value = 50, message = "{store.size.max}")
        Integer size
) {
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
