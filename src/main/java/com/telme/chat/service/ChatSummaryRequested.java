package com.telme.chat.service;

import java.util.Objects;

public record ChatSummaryRequested(
        Long executionId,
        Long sessionId,
        Integer completedThroughSequenceNo
) {

    public ChatSummaryRequested {
        Objects.requireNonNull(executionId, "executionId");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(completedThroughSequenceNo, "completedThroughSequenceNo");
        if (completedThroughSequenceNo < 1) {
            throw new IllegalArgumentException("완료 메시지 순번은 1 이상이어야 합니다.");
        }
    }
}
