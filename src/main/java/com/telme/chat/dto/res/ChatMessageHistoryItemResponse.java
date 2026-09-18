package com.telme.chat.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ChatMessageHistoryItemResponse(
        Long messageId,
        Integer sequenceNo,
        Long replyToMessageId,
        String role,
        String messageType,
        String content,
        String status,
        String answerBasis,
        @Schema(description = "후속 질문 목록", example = "[\"변경 방법도 알려줘\"]")
        List<String> followUps,
        @Schema(
                description = "추천 매장 스냅샷",
                example = "[{\"storeId\":2,\"name\":\"텔미 부산점\"}]"
        )
        List<Map<String, Object>> storeResults,
        Instant createdAt,
        Instant completedAt
) {
}
