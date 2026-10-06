package com.telme.chat.dto.res;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import lombok.Builder;

@Builder
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ChatExecutionTraceResponse(
        Long executionId,
        Long sessionId,
        Long inputMessageId,
        Long outputMessageId,
        String status,
        String errorCode,
        String originalUserMessage,
        String finalAnswer,
        String answerStatus,
        String answerBasis,
        boolean traceRecorded,
        JsonNode steps,
        List<Map<String, Object>> routing,
        List<Map<String, Object>> modelAttempts
) {
}
