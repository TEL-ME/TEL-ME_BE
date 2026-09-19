package com.telme.llm.dto.req;

import com.telme.llm.entity.LlmGeneration.TaskType;

import lombok.Builder;

@Builder
public record LlmRequest(
        // null이면 호출 기록을 남기지 않음
        Long executionId,
        TaskType taskType,
        String systemPrompt,
        String userPrompt,
        ResponseFormat format,
        // null이면 TaskType별 기본값 사용
        Double temperature,
        Integer maxTokens
) {

    public LlmRequest {
        // 대부분 TEXT라 기본값으로 둠
        if (format == null) {
            format = ResponseFormat.TEXT;
        }
    }
}
