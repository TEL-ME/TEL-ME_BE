package com.telme.chat.dto.res;

import java.time.Instant;

public record AdminUnansweredListItemResponse(
        Long messageId,
        Long sessionId,
        String type,
        // 목록에서 무엇을 물었는지 바로 보이도록 질문 앞부분을 함께 준다
        String questionPreview,
        Instant createdAt
) {
}
