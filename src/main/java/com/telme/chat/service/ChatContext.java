package com.telme.chat.service;

import java.util.List;
import java.util.Objects;

public record ChatContext(
        Long sessionId,
        Long inputMessageId,
        String summary,
        List<ChatContextMessage> history,
        String currentQuestion,
        int estimatedContextTokens
) {

    public ChatContext {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(inputMessageId, "inputMessageId");
        Objects.requireNonNull(history, "history");
        if (currentQuestion == null || currentQuestion.isBlank()) {
            throw new IllegalArgumentException("현재 질문은 비어 있을 수 없습니다.");
        }
        if (estimatedContextTokens < 0) {
            throw new IllegalArgumentException("예상 토큰 수는 음수일 수 없습니다.");
        }
        summary = summary == null || summary.isBlank() ? null : summary;
        history = List.copyOf(history);
    }
}
