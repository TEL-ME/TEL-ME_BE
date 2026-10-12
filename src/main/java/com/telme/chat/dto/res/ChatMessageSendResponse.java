package com.telme.chat.dto.res;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.telme.chat.guard.InputGuardNotice;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatMessageSendResponse(
        Long sessionId,
        Long messageId,
        Integer sequenceNo,
        Long executionId,
        String executionStatus,
        Instant createdAt,
        InputGuardNotice inputGuard
) {
    public ChatMessageSendResponse(Long sessionId, Long messageId, Integer sequenceNo,
            Long executionId, String executionStatus, Instant createdAt) {
        this(sessionId, messageId, sequenceNo, executionId, executionStatus, createdAt, null);
    }

    public ChatMessageSendResponse withInputGuard(InputGuardNotice notice) {
        return new ChatMessageSendResponse(sessionId, messageId, sequenceNo, executionId,
                executionStatus, createdAt, notice);
    }

    public boolean accepted() {
        return executionId != null;
    }
}
