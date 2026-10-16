package com.telme.chat.dto.res;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.Builder;

@Builder
@JsonInclude(JsonInclude.Include.ALWAYS)
public record AdminChatMonitoringDetailResponse(
        Long executionId,
        Long sessionId,
        Long inputMessageId,
        Long outputMessageId,
        Long userId,
        String userName,
        String guestId,
        String status,
        String errorCode,
        Instant startedAt,
        Instant endedAt,
        Long elapsedMs,
        String originalUserMessage,
        String finalAnswer,
        String answerStatus,
        String answerBasis,
        List<String> inputCheckActions,
        AdminChatMonitoringStagesResponse stages,
        // 단계 기록 순서
        List<AdminChatMonitoringTimelineResponse> timeline,
        boolean traceRecorded,
        JsonNode steps,
        List<Map<String, Object>> routing,
        // 모델 호출 + createdAt
        List<Map<String, Object>> modelAttempts
) {
}
