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
        Instant completedAt,
        @Schema(description = "현재 사용자가 이 메세지를 평가할 수 있는지 여부")
        boolean ratable,
        @Schema(description = "현재 사용자가 남긴 평가, 미작성이면 null")
        MyFeedback myFeedback
) {
    public record MyFeedback(String rating, String reason, String comment) {
        
    }
}
