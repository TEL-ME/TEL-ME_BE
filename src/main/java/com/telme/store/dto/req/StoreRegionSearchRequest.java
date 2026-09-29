package com.telme.store.dto.req;

import com.telme.store.entity.StoreServiceType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record StoreRegionSearchRequest(
        @Schema(description = "법정동코드 앞자리 (시도 2자리, 시군구 5자리 등)", example = "11680",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "지역 코드를 입력해 주세요.")
        @Pattern(regexp = "\\d{2,10}", message = "지역 코드는 2~10자리 숫자여야 합니다.")
        String region,

        @Schema(description = "가능 업무. 비우면 전체")
        StoreServiceType.Code serviceType
) {
}
