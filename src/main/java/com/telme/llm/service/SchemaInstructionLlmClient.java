package com.telme.llm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.llm.dto.req.LlmRequest;
import java.util.Objects;

/**
 * Bedrock 요청 변환기는 JSON 스키마를 보내지 않는다(gpt-oss에 출력 스키마를 강제하면 깨진 JSON이 나온다).
 * 스키마를 사용자 프롬프트 끝에 지시문으로 붙여 보낸다. 평가용 중계기(scripts/judge-serving-evaluation/judge_router.py)와
 * 같은 문구다. 응답 검증은 호출하는 쪽이 그대로 한다.
 */
public class SchemaInstructionLlmClient implements LlmClient {

    static final String INSTRUCTION = "\n\n다음 JSON Schema를 따르는 JSON 객체 하나만 출력하세요.\n";

    private final LlmClient delegate;
    private final ObjectMapper mapper;

    public SchemaInstructionLlmClient(LlmClient delegate, ObjectMapper mapper) {
        this.delegate = Objects.requireNonNull(delegate);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public String generate(LlmRequest request) {
        return delegate.generate(withInstruction(request));
    }

    @Override
    public void stream(LlmRequest request, LlmStreamHandler handler) {
        delegate.stream(withInstruction(request), handler);
    }

    private LlmRequest withInstruction(LlmRequest request) {
        if (request.responseSchema() == null) {
            return request;
        }
        String schema;
        try {
            schema = mapper.writeValueAsString(request.responseSchema());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("응답 스키마를 지시문으로 바꾸지 못했습니다.", e);
        }
        return LlmRequest.builder()
                .executionId(request.executionId())
                .consultRequestId(request.consultRequestId())
                .taskType(request.taskType())
                .systemPrompt(request.systemPrompt())
                .userPrompt(request.userPrompt() + INSTRUCTION + schema)
                .format(request.format())
                .temperature(request.temperature())
                .maxTokens(request.maxTokens())
                .contextCount(request.contextCount())
                .promptVersion(request.promptVersion())
                .responseSchema(request.responseSchema())
                .think(request.think())
                .build();
    }
}
