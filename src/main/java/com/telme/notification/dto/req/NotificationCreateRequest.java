package com.telme.notification.dto.req;

import com.telme.notification.dto.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record NotificationCreateRequest(
        @Schema(description = "알림 종류. 지금은 ANSWER_ARRIVED(새 답변 도착)만 있습니다.", example = "ANSWER_ARRIVED")
        @NotNull NotificationType type,
        @Schema(description = "답변이 온 채팅방 ID", example = "12")
        @NotNull @Positive Long sessionId,
        @Schema(description = "도착한 챗봇 답변 메시지 ID", example = "345")
        @NotNull @Positive Long messageId) {
}
