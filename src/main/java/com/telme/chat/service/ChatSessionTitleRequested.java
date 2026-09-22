package com.telme.chat.service;

import java.util.Objects;

record ChatSessionTitleRequested(
        Long executionId,
        Long sessionId,
        String question
) {

    ChatSessionTitleRequested {
        Objects.requireNonNull(executionId, "executionId");
        Objects.requireNonNull(sessionId, "sessionId");
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("세션 제목 생성 기준 질문은 비어 있을 수 없습니다.");
        }
        question = question.trim();
    }
}
