package com.telme.subscription.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;

public record SubscribeRequest(
        @Schema(description = "가입할 요금제 ID")
        @NotNull(message = "요금제를 선택해 주세요.")
        @Positive(message = "요금제 ID는 1 이상이어야 합니다.") Long planId,
        @Schema(description = "생년월일(yyyy-MM-dd). 청소년·시니어 요금제이고 저장된 생년월일이 없을 때만 필요")
        LocalDate birthDate,
        @Schema(description = "약정 가입 여부(24개월, 월정액 25% 할인). 생략하면 약정 없음")
        boolean contract
) {
}
