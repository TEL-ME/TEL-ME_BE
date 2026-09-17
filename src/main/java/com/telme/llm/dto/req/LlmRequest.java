package com.telme.llm.dto.req;

import com.telme.llm.entity.LlmGeneration.TaskType;

import lombok.Builder;

@Builder
public record LlmRequest(
        TaskType taskType,
        String systemPrompt,
        String userPrompt,
        ResponseFormat format,
        // null이면 모델 기본값을 사용
        Double temperature,
        Integer maxTokens
) {

    public LlmRequest {
        // 대부분 TEXT라 기본값으로 둠
        if (format == null) {
            format = ResponseFormat.TEXT;
        }
    }

    public enum ResponseFormat {
        TEXT, JSON
    }
}
