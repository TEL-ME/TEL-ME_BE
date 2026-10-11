package com.telme.llm.dto.req;

import com.telme.llm.entity.LlmGeneration.TaskType;

import lombok.Builder;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Builder
public record LlmRequest(
        // null이면 호출 기록을 남기지 않음
        Long executionId,
        Long consultRequestId,
        TaskType taskType,
        String systemPrompt,
        String userPrompt,
        ResponseFormat format,
        // null이면 TaskType별 기본값 사용
        Double temperature,
        Integer maxTokens,
        // 호출 기록용. Context에 넣은 근거 수
        Integer contextCount,
        // 호출 기록용. 사용한 프롬프트 버전
        String promptVersion,
        // 응답을 제한할 JSON Schema. 지정하면 JSON 응답으로 요청한다. null이면 형식 제한 없음
        Map<String, Object> responseSchema,
        // false면 사고 모드를 지원하는 모델에서 끈다. null이면 모델 기본값
        Boolean think
) {
    public LlmRequest(Long executionId, TaskType taskType, String systemPrompt, String userPrompt,
            ResponseFormat format, Double temperature, Integer maxTokens, Integer contextCount,
            String promptVersion) {
        this(executionId, null, taskType, systemPrompt, userPrompt, format, temperature, maxTokens,
                contextCount, promptVersion, null, null);
    }

    public LlmRequest {
        // 대부분 TEXT라 기본값으로 둠
        if (format == null) {
            format = ResponseFormat.TEXT;
        }
        if (responseSchema != null) {
            format = ResponseFormat.JSON;
            // 속성 순서가 모델의 생성 순서에 영향을 줄 수 있어 순서를 보존한다.
            responseSchema = Collections.unmodifiableMap(new LinkedHashMap<>(responseSchema));
        }
    }
}
