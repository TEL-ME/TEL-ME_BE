package com.telme.llm.dto.res;

import java.time.Instant;

public record AdminLlmErrorListItemResponse(
        Long generationId,
        Instant createdAt,
        String errorType,
        String taskType,
        int attempt,
        String model,
        Integer totalMs,
        String errorMessage,
        Long executionId
        ) {
}
