package com.telme.notification.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;

public record NotificationReadResponse(
        @Schema(description = "이번에 읽음 처리한 알림 수. 이미 다 읽었으면 0")
        int readCount) {
}
