package com.telme.chat.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "게스트 하루 질문 수 제한 상태. 회원이거나 제한을 끈 환경이면 limited=false이고 나머지는 null")
public record ChatGuestLimitResponse(
        @Schema(description = "하루 질문 수 제한을 받는지 여부")
        boolean limited,
        @Schema(description = "하루에 보낼 수 있는 질문 수", nullable = true)
        Integer dailyLimit,
        @Schema(description = "오늘 남은 질문 수. 실패·취소된 질문은 쓰지 않은 것으로 본다", nullable = true)
        Integer remaining,
        @Schema(description = "한도가 다시 차는 시각(다음 한국 시간 0시)", nullable = true)
        Instant resetsAt
) {

    public static ChatGuestLimitResponse unlimited() {
        return new ChatGuestLimitResponse(false, null, null, null);
    }
}
