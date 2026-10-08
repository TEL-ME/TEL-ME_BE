package com.telme.chat.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "현재 회원 또는 게스트의 채팅 입력 제한 상태")
public record ChatInputGuardStatusResponse(
        @Schema(description = "서버 시각 기준으로 제한이 유효한지 여부")
        boolean restricted,
        @Schema(description = "유효한 제한의 종료 시각. 미제한 또는 만료 상태에서는 null", nullable = true)
        Instant restrictionUntil,
        @Schema(description = "남은 제한 시간. 초 미만을 올림하며 미제한이면 0", minimum = "0")
        long retryAfterSeconds,
        @Schema(description = "조회 결과 계산에 사용한 서버 시각")
        Instant serverTime
) {
}
