package com.telme.chat.dto.req;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record AdminChatMonitoringSearchRequest(
        // 이 메시지 ID 미만만 조회(생략 시 최신)
        @Min(1) Long beforeId,
        @Min(1) @Max(50) Integer size,
        AdminChatMonitoringStatus status
) {
    private static final int DEFAULT_SIZE = 20;

    public AdminChatMonitoringSearchRequest {
        size = size == null ? DEFAULT_SIZE : size;
    }

    public long beforeIdOrMax() {
        return beforeId == null ? Long.MAX_VALUE : beforeId;
    }
}
