package com.telme.subscription.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record PauseRequest(
        @Schema(description = "일시 정지 일수(1~30)")
        @NotNull(message = "정지할 일수를 입력해 주세요.") Integer days
) {
}
