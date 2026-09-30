package com.telme.feedback.dto.res;

import java.time.Instant;

public record AdminFeedbackListItemResponse(
        Long feedbackId,
        String reasonCode,
        // 목록에서 무엇에 대한 불만인지 바로 보이도록 질문 앞부분을 함께 준다
        String questionPreview,
        String commentPreview,
        Instant createdAt,
        boolean handled
) {
}
