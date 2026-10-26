package com.telme.chat.dto.res;

import java.time.Instant;

public record ChatSessionCreateResponse(
        Long sessionId,
        String title,
        String status,
        Instant createdAt,
        Instant lastActiveAt
) {
}
