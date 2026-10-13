package com.telme.subscription.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;

public record ChangePlanRequest(
        @Schema(description = "변경할 요금제 ID")
        @NotNull(message = "요금제를 선택해 주세요.")
        @Positive(message = "요금제 ID는 1 이상이어야 합니다.") Long planId,
        @Schema(description = "생년월일(yyyy-MM-dd). 청소년·시니어 요금제이고 저장된 생년월일이 없을 때만 필요")
        LocalDate birthDate
) {
}
