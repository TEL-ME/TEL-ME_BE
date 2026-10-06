package com.telme.chat.dto.res;

import java.time.Instant;

public record AdminUnansweredListItemResponse(
        Long messageId,
        Long sessionId,
        String type,
        String questionPreview,
        Instant createdAt
) {
}
