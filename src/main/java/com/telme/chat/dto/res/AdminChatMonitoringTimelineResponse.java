package com.telme.chat.dto.res;

import java.time.Instant;

public record AdminChatMonitoringTimelineResponse(
        String stage,
        Instant at,
        // 실행 시작 기준 경과 ms
        Long elapsedMs
) {
}
