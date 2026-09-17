package com.telme.chat.dto.res;

import java.time.Instant;

public record ChatMessageSendResponse(
        Long sessionId,
        Long messageId,
        Integer sequenceNo,
        Long executionId,
        String executionStatus,
        Instant createdAt
) {
}
