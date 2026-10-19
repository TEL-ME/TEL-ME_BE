package com.telme.probe;

import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.service.LlmClient;
import com.telme.llm.service.LlmStreamHandler;
import java.util.concurrent.atomic.AtomicInteger;

// 평가의 유료 모델 호출 수 집계(평가 호출은 executionId가 없어 DB에 남지 않음)
final class CountingLlmClient implements LlmClient {

    private final LlmClient delegate;
    private final AtomicInteger calls = new AtomicInteger();

    CountingLlmClient(LlmClient delegate) {
        this.delegate = delegate;
    }

    int calls() {
        return calls.get();
    }

    @Override
    public String generate(LlmRequest request) {
        calls.incrementAndGet();
        return delegate.generate(request);
    }

    @Override
    public void stream(LlmRequest request, LlmStreamHandler handler) {
        calls.incrementAndGet();
        delegate.stream(request, handler);
    }
}
