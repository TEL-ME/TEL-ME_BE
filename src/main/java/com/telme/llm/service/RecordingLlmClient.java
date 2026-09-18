package com.telme.llm.service;

import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.service.LlmGenerationRecorder.Result;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class RecordingLlmClient implements LlmClient {

    private final LlmClient delegate;
    private final LlmGenerationRecorder recorder;
    private final String model;

    @Override
    public String generate(LlmRequest request) {
        long startedAt = System.currentTimeMillis();
        try {
            String result = delegate.generate(request);
            recorder.record(request, model, Result.success(null, elapsed(startedAt)));
            return result;
        } catch (RuntimeException e) {
            recorder.record(request, model, Result.failure(e, null, elapsed(startedAt)));
            throw e;
        }
    }

    @Override
    public void stream(LlmRequest request, LlmStreamHandler handler) {
        long startedAt = System.currentTimeMillis();
        RecordingHandler wrapper = new RecordingHandler(handler, startedAt);

        delegate.stream(request, wrapper);

        Result result = wrapper.error == null
                ? Result.success(wrapper.firstTokenMs, elapsed(startedAt))
                : Result.failure(wrapper.error, wrapper.firstTokenMs, elapsed(startedAt));
        recorder.record(request, model, result);
    }

    private long elapsed(long startedAt) {
        return System.currentTimeMillis() - startedAt;
    }

    // 첫 토큰 시각과 최종 결과를 기록용으로 관찰한다. 전달은 그대로 한다
    private static final class RecordingHandler implements LlmStreamHandler {

        private final LlmStreamHandler delegate;
        private final long startedAt;
        private Integer firstTokenMs;
        private Throwable error;

        private RecordingHandler(LlmStreamHandler delegate, long startedAt) {
            this.delegate = delegate;
            this.startedAt = startedAt;
        }

        @Override
        public void onToken(String token) {
            if (firstTokenMs == null) {
                firstTokenMs = (int) (System.currentTimeMillis() - startedAt);
            }
            delegate.onToken(token);
        }

        @Override
        public void onComplete() {
            delegate.onComplete();
        }

        @Override
        public void onError(Throwable error) {
            this.error = error;
            delegate.onError(error);
        }

        @Override
        public void onRetry(int attempt, Throwable cause) {
            delegate.onRetry(attempt, cause);
        }
    }
}
