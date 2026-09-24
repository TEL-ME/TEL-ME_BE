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
        @Schema(description = "피드백 기능이 켜져 있고, 완료된 ASSISTANT ANSWER 또는 STORE_RESULT이면 true")
        boolean ratable,
        @Schema(description = "현재 사용자가 남긴 평가. 미작성 또는 피드백 기능 비활성화 시 null")
        MyFeedback myFeedback
) {
    public record MyFeedback(
            String rating,
            @Schema(description = "DISLIKE 사유 코드: WRONG_INFO(정보 오류), NOT_RELATED(관련 없음), "
                    + "HARD_TO_READ(읽기 어려움). LIKE이면 null") String reason,
            String comment) {
    }
}
