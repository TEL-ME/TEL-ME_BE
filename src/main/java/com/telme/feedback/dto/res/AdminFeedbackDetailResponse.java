package com.telme.feedback.dto.res;

import java.time.Instant;
import java.util.List;

public record AdminFeedbackDetailResponse(
        Long feedbackId,
        String reasonCode,
        String comment,
        Instant createdAt,
        Instant updatedAt,
        boolean handled,
        Instant handledAt,
        Long handledBy,
        String handledNote,
        Long messageId,
        String question,
        String answer,
        // GROUNDED / NO_EVIDENCE / OUT_OF_SCOPE. 근거 없이 답한 건지 바로 보인다
        String answerBasis,
        Instant answeredAt,
        List<AdminFeedbackSourceResponse> sources
) {
}
