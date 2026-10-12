package com.telme.chat.dto.res;

import java.time.Instant;

public record AdminUnansweredListItemResponse(
        Long messageId,
        Long sessionId,
        String type,
        String questionPreview,
        // 되묻기가 있었으면 questionPreview는 조건 답변이라, 상담을 시작한 원문을 함께 준다
        String originQuestionPreview,
        Instant createdAt
) {
}
