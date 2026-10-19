package com.telme.llm.service;

import com.telme.llm.dto.req.LlmRequest;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

// 주 모델 호출이 실패하면 대체 모델로 다시 부른다. 스트리밍은 첫 글자를 보내기 전에 실패했을 때만 대체한다
// (글자가 나간 뒤 대체하면 답이 섞인다)
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
        BeforeFirstTokenHandler guarded = new BeforeFirstTokenHandler(handler);
        try {
            primary.stream(request, guarded);
        } catch (RuntimeException failure) {
            guarded.onError(failure);
        }
        if (guarded.failedBeforeFirstToken) {
            log.warn("[LLM 대체] 주 모델 스트리밍이 첫 글자 전에 실패해 대체 모델로 다시 부릅니다. taskType={}",
                    request.taskType());
            fallback.stream(request, handler);
        }
    }

    // 첫 글자 전 실패는 호출자에게 넘기지 않고 대체 신호로만 남긴다. 글자가 나간 뒤의 실패는 그대로 넘긴다
    private static final class BeforeFirstTokenHandler implements LlmStreamHandler {

        private final LlmStreamHandler delegate;
        private boolean started;
        private boolean failedBeforeFirstToken;

        private BeforeFirstTokenHandler(LlmStreamHandler delegate) {
            this.delegate = delegate;
        }

        @Override
        public void onToken(String token) {
            started = true;
            delegate.onToken(token);
        }

        @Override
        public void onComplete() {
            delegate.onComplete();
        }

        @Override
        public void onError(Throwable error) {
            if (started) {
                delegate.onError(error);
                return;
            }
            failedBeforeFirstToken = true;
        }

        @Override
        public void onProgress() {
            delegate.onProgress();
        }

        @Override
        public void onRetry(int attempt, Throwable cause) {
            delegate.onRetry(attempt, cause);
        }
    }
}
