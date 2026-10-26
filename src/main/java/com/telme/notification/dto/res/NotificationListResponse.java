package com.telme.notification.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record NotificationListResponse(
        List<NotificationResponse> notifications,
        @Schema(description = "다음 페이지 요청의 cursor 값. 마지막 페이지면 null")
        Long nextCursor,
        boolean hasNext) {
}
