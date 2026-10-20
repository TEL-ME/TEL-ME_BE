package com.telme.notification.dto.res;

import com.telme.notification.dto.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record NotificationResponse(
        Long notificationId,
        NotificationType type,
        @Schema(description = "채팅방 제목. 제목이 만들어지기 전이면 null")
        String title,
        @Schema(description = "답변 앞부분. 줄바꿈은 공백으로 바꾸고 길면 ...으로 줄입니다.")
        String body,
        @Schema(description = "누르면 이동할 채팅방 ID")
        Long sessionId,
        @Schema(description = "도착한 답변 메시지 ID")
        Long messageId,
        Instant createdAt,
        boolean read) {
}
