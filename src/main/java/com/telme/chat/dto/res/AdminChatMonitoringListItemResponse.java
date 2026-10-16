package com.telme.chat.dto.res;

import java.time.Instant;
import lombok.Builder;

@Builder
public record AdminChatMonitoringListItemResponse(
        Long messageId,
        // 차단이면 null
        Long executionId,
        Long sessionId,
        // 회원: userId, userName / 게스트: guestId
        Long userId,
        String userName,
        String guestId,
        // RUNNING, COMPLETED, FAILED, CANCELLED, BLOCKED
        String status,
        Instant startedAt,
        Instant endedAt,
        Long elapsedMs,
        // 질문 앞 40자
        String questionPreview,
        String intent,
        String answerBasis,
        String errorCode,
        // 마지막 기록 단계
        String lastStage,
        AdminChatMonitoringStagesResponse stages
) {
}
