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
        for (int attempt = 1; attempt <= maxAttempts(); attempt++) {
            try {
                return delegate.generate(request);
            } catch (RuntimeException e) {
                last = e;
                // 다시 호출해도 같은 결과인 오류는 재시도하지 않는다
                if (!retryable(e) || attempt == maxAttempts()) {
                    break;
                }
                sleep();
            }
        }
        throw last;
    }

    @Override
    public void stream(LlmRequest request, LlmStreamHandler handler) {
        for (int attempt = 1; attempt <= maxAttempts(); attempt++) {
            RetryAwareHandler wrapper = new RetryAwareHandler(handler);
            delegate.stream(request, wrapper);

            if (wrapper.error == null) {
                return;
            }
            // 토큰이 이미 나갔으면 다시 호출하지 않는다. 답변이 중복된다
            if (wrapper.tokenSent || !retryable(wrapper.error) || attempt == maxAttempts()) {
                handler.onError(wrapper.error);
                return;
            }
            handler.onRetry(attempt, wrapper.error);
            sleep();
        }
    }

    // 연결 실패·응답 시간 초과처럼 다시 호출하면 해결될 수 있는 오류만 재시도한다
    private boolean retryable(Throwable error) {
        return error instanceof GeneralException general
                && (general.getErrorCode() == LlmErrorCode.CONNECTION_FAILED
                        || general.getErrorCode() == LlmErrorCode.TIMEOUT);
    }

    // 설정이 0 이하로 들어와도 최소 한 번은 호출한다
    private int maxAttempts() {
        return Math.max(1, properties.maxAttempts());
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
