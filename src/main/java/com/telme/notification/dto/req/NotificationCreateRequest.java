package com.telme.notification.dto.req;

import com.telme.notification.dto.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record NotificationCreateRequest(
        @Schema(description = "알림 종류. ANSWER_ARRIVED(답변·되묻기·매장 결과 도착), ANSWER_FAILED(답변 실패·시간 초과)",
                example = "ANSWER_ARRIVED")
        @NotNull NotificationType type,
        @Schema(description = "답변이 온 채팅방 ID", example = "12")
        @NotNull @Positive Long sessionId,
        @Schema(description = "알림을 띄운 챗봇 메시지 ID(실패면 실패한 답변 메시지)", example = "345")
        @NotNull @Positive Long messageId) {
}
