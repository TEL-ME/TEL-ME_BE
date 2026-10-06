package com.telme.llm.service;

import com.telme.global.common.exception.GeneralException;
import com.telme.llm.config.LlmRetryProperties;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.exception.LlmErrorCode;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class RetryingLlmClient implements LlmClient {

    private final LlmAttemptClient delegate;
    private final LlmRetryProperties properties;

    @Override
    public String generate(LlmRequest request) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= maxAttempts(); attempt++) {
            try {
                return delegate.generate(request, attempt);
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
            try {
                delegate.stream(request, wrapper, attempt);
            } catch (RuntimeException failure) {
                // 모델의 동기 예외뿐 아니라 delegate.stream 호출 중 하위 onToken/onComplete에서
                // 전파된 RuntimeException도 보관한다. 기존 정책으로 재시도 여부를 판단한 뒤
                // 최종 실패는 아래 handler.onError로 전달한다. 현재 RagAnswerGenerator는
                // onError로 보관한 오류를 stream 종료 후 다시 던진다. 최종 오류·재시도 콜백이나
                // 재시도 대기에서 발생한 예외까지 모두 이 catch에서 처리하는 계약은 아니다.
                wrapper.onError(failure);
            }

            if (wrapper.error == null) {
                return;
            }
            // 원문 토큰을 이미 받았으면 기존 정책대로 재시도하지 않는다.
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
