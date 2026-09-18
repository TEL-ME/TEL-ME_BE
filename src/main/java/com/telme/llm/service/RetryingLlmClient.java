package com.telme.llm.service;

import com.telme.global.common.exception.GeneralException;
import com.telme.llm.config.LlmRetryProperties;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.exception.LlmErrorCode;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class RetryingLlmClient implements LlmClient {

    private final LlmClient delegate;
    private final LlmRetryProperties properties;

    @Override
    public String generate(LlmRequest request) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= properties.maxAttempts(); attempt++) {
            try {
                return delegate.generate(request);
            } catch (RuntimeException e) {
                last = e;
                if (attempt == properties.maxAttempts()) {
                    break;
                }
                sleep();
            }
        }
        throw last;
    }

    @Override
    public void stream(LlmRequest request, LlmStreamHandler handler) {
        for (int attempt = 1; attempt <= properties.maxAttempts(); attempt++) {
            RetryAwareHandler wrapper = new RetryAwareHandler(handler);
            delegate.stream(request, wrapper);

            if (wrapper.error == null) {
                return;
            }
            // 토큰이 이미 나갔으면 다시 호출하지 않는다. 답변이 중복된다
            if (wrapper.tokenSent || attempt == properties.maxAttempts()) {
                handler.onError(wrapper.error);
                return;
            }
            handler.onRetry(attempt, wrapper.error);
            sleep();
        }
    }

    private void sleep() {
        try {
            Thread.sleep(properties.waitDuration().toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GeneralException(LlmErrorCode.STREAM_INTERRUPTED);
        }
    }

    // 실패를 바로 알리지 않고 붙잡아 둔다. 재시도 여부를 정한 뒤 전달한다
    private static final class RetryAwareHandler implements LlmStreamHandler {

        private final LlmStreamHandler delegate;
        private boolean tokenSent;
        private Throwable error;

        private RetryAwareHandler(LlmStreamHandler delegate) {
            this.delegate = delegate;
        }

        @Override
        public void onToken(String token) {
            tokenSent = true;
            delegate.onToken(token);
        }

        @Override
        public void onComplete() {
            delegate.onComplete();
        }

        @Override
        public void onError(Throwable error) {
            this.error = error;
        }
    }
}
