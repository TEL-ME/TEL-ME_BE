package com.telme.llm.service;

import com.telme.llm.dto.req.LlmRequest;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

// 주 모델 호출 실패 시 대체 모델로 재호출, 스트리밍은 대체 모델 처리
@Slf4j
public class FallbackLlmClient implements LlmClient {

    private final LlmClient primary;
    private final LlmClient fallback;

    public FallbackLlmClient(LlmClient primary, LlmClient fallback) {
        this.primary = Objects.requireNonNull(primary);
        this.fallback = Objects.requireNonNull(fallback);
    }

    @Override
    public String generate(LlmRequest request) {
        try {
            return primary.generate(request);
        } catch (RuntimeException failure) {
            log.warn("[LLM 대체] 주 모델 호출이 실패해 대체 모델로 다시 부릅니다. taskType={}", request.taskType());
            return fallback.generate(request);
        }
    }

    @Override
    public void stream(LlmRequest request, LlmStreamHandler handler) {
        fallback.stream(request, handler);
    }
}
