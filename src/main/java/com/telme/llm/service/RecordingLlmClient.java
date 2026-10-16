package com.telme.llm.service;

import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.service.LlmGenerationRecorder.Result;

import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

@Slf4j
public class RecordingLlmClient implements LlmAttemptClient {

    private final LlmClient delegate;
    private final LlmGenerationRecorder recorder;
    private final String model;
    // null이면 기존 Ollama 기록 경로를 쓴다
    private final String provider;
    private final LongSupplier nanoTime;

    public RecordingLlmClient(LlmClient delegate, LlmGenerationRecorder recorder, String model) {
        this(delegate, recorder, model, null);
    }

    public RecordingLlmClient(LlmClient delegate, LlmGenerationRecorder recorder, String model, String provider) {
        this(delegate, recorder, model, provider, System::nanoTime);
    }

    RecordingLlmClient(LlmClient delegate, LlmGenerationRecorder recorder, String model,
            String provider, LongSupplier nanoTime) {
        this.delegate = delegate;
        this.recorder = recorder;
        this.model = model;
        this.provider = provider;
        this.nanoTime = nanoTime;
    }

    @Override
    public String generate(LlmRequest request, int attempt) {
        long startedAt = nanoTime.getAsLong();
        try {
            String result = delegate.generate(request);
            safeRecord(request, Result.success(attempt, null, elapsed(startedAt)));
            return result;
        } catch (RuntimeException e) {
            safeRecord(request, Result.failure(attempt, e, null, elapsed(startedAt)));
            throw e;
        }
    }

    @Override
    public void stream(LlmRequest request, LlmStreamHandler handler, int attempt) {
        long startedAt = nanoTime.getAsLong();
        RecordingHandler wrapper = new RecordingHandler(handler, startedAt);

        try {
            delegate.stream(request, wrapper);
        } catch (RuntimeException e) {
            // 안쪽에서 예외가 그대로 올라와도 기록은 남긴다
            safeRecord(request, Result.failure(attempt, e, wrapper.firstTokenMs, elapsed(startedAt)));
            throw e;
        }

        Result result = wrapper.error == null
                ? Result.success(attempt, wrapper.firstTokenMs, elapsed(startedAt))
                : Result.failure(attempt, wrapper.error, wrapper.firstTokenMs, elapsed(startedAt));
        safeRecord(request, result);
    }

    // 기록 저장 실패가 LLM 결과나 원래 예외를 덮지 않도록 여기서 막는다
    private void safeRecord(LlmRequest request, Result result) {
        try {
            if (provider == null) {
                recorder.record(request, model, result);
            } else {
                recorder.record(request, model, provider, result);
            }
        } catch (RuntimeException e) {
            log.warn("[RecordingLlmClient] 호출 기록 저장 실패 executionId={}", request.executionId(), e);
        }
    }

    private long elapsed(long startedAt) {
        // 경과 시간은 시스템 시계 보정과 무관한 단조 시계로 측정한다.
        return TimeUnit.NANOSECONDS.toMillis(nanoTime.getAsLong() - startedAt);
    }

    // 첫 토큰 시각과 최종 결과를 기록용으로 관찰한다. 전달은 그대로 한다
    private final class RecordingHandler implements LlmStreamHandler {

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
                firstTokenMs = Result.recordableMillis(elapsed(startedAt));
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
