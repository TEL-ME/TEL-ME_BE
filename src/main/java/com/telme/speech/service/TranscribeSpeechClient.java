package com.telme.speech.service;

import com.telme.global.common.exception.GeneralException;
import com.telme.speech.config.SpeechSttProperties;
import com.telme.speech.exception.SpeechErrorCode;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.transcribestreaming.TranscribeStreamingAsyncClient;
import software.amazon.awssdk.services.transcribestreaming.model.Alternative;
import software.amazon.awssdk.services.transcribestreaming.model.MediaEncoding;
import software.amazon.awssdk.services.transcribestreaming.model.Result;
import software.amazon.awssdk.services.transcribestreaming.model.StartStreamTranscriptionRequest;
import software.amazon.awssdk.services.transcribestreaming.model.StartStreamTranscriptionResponseHandler;
import software.amazon.awssdk.services.transcribestreaming.model.TranscriptEvent;

// Transcribe 실시간 인식 호출만 맡는다. 인식된 문장은 사용자 발화라 로그에 남기지 않는다
@Slf4j
@Component
@RequiredArgsConstructor
public class TranscribeSpeechClient {

    private final TranscribeStreamingAsyncClient transcribeClient;
    private final SpeechSttProperties properties;

    public String transcribe(byte[] pcm, int sampleRate) {
        StartStreamTranscriptionRequest.Builder requestBuilder = StartStreamTranscriptionRequest.builder()
                .languageCode(properties.languageCode())
                .mediaEncoding(MediaEncoding.PCM)
                .mediaSampleRateHertz(sampleRate);
        if (properties.vocabularyName() != null && !properties.vocabularyName().isBlank()) {
            requestBuilder.vocabularyName(properties.vocabularyName().strip());
        }
        StartStreamTranscriptionRequest request = requestBuilder.build();
        TranscriptCollector collector = new TranscriptCollector();
        StartStreamTranscriptionResponseHandler handler = StartStreamTranscriptionResponseHandler.builder()
                .subscriber(event -> {
                    if (event instanceof TranscriptEvent transcriptEvent) {
                        collector.add(transcriptEvent.transcript().results());
                    }
                })
                .build();

        CompletableFuture<Void> call;
        try {
            call = transcribeClient.startStreamTranscription(request, new AudioChunkPublisher(pcm, sampleRate), handler);
        } catch (SdkException e) {
            logFailure(e);
            throw unavailable();
        }
        try {
            call.get(properties.timeout().toMillis(), TimeUnit.MILLISECONDS);
            return collector.text();
        } catch (TimeoutException e) {
            call.cancel(true);
            log.warn("[Transcribe] 응답 시간 초과 {}ms", properties.timeout().toMillis());
            throw unavailable();
        } catch (InterruptedException e) {
            call.cancel(true);
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (ExecutionException e) {
            logFailure(e.getCause());
            throw unavailable();
        }
    }

    private void logFailure(Throwable failure) {
        Throwable cause = failure;
        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof AwsServiceException e) {
            String errorCode = e.awsErrorDetails() == null ? null : e.awsErrorDetails().errorCode();
            if (e.statusCode() >= 400 && e.statusCode() < 500) {
                log.error("[Transcribe] 설정·권한·한도 오류 status={}, code={}", e.statusCode(), errorCode);
            } else {
                log.warn("[Transcribe] 서버 오류 status={}, code={}", e.statusCode(), errorCode);
            }
            return;
        }
        log.warn("[Transcribe] 호출 실패 {}: {}", cause == null ? "unknown" : cause.getClass().getSimpleName(),
                cause == null ? null : cause.getMessage());
    }

    private static GeneralException unavailable() {
        return new GeneralException(SpeechErrorCode.TRANSCRIPTION_UNAVAILABLE);
    }

    // 중간 결과는 계속 고쳐지므로 확정된 결과만 순서대로 이어 붙인다
    static final class TranscriptCollector {

        private final StringBuilder text = new StringBuilder();

        synchronized void add(List<Result> results) {
            for (Result result : results) {
                if (Boolean.TRUE.equals(result.isPartial()) || result.alternatives().isEmpty()) {
                    continue;
                }
                Alternative best = result.alternatives().get(0);
                if (best.transcript() != null && !best.transcript().isBlank()) {
                    if (!text.isEmpty()) {
                        text.append(' ');
                    }
                    text.append(best.transcript().strip());
                }
            }
        }

        synchronized String text() {
            return text.toString();
        }
    }
}
