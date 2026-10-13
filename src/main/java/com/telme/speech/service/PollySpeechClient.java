package com.telme.speech.service;

import com.telme.global.common.exception.GeneralException;
import com.telme.speech.config.SpeechTtsProperties;
import com.telme.speech.exception.SpeechErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.polly.PollyClient;
import software.amazon.awssdk.services.polly.model.OutputFormat;
import software.amazon.awssdk.services.polly.model.SynthesizeSpeechRequest;
import software.amazon.awssdk.services.polly.model.TextType;

// Polly 호출만 맡는다. 답변 문장은 사용자 대화라 로그에 남기지 않는다
@Slf4j
@Component
@RequiredArgsConstructor
public class PollySpeechClient {

    private final PollyClient pollyClient;
    private final SpeechTtsProperties properties;

    public byte[] synthesize(String text) {
        SynthesizeSpeechRequest request = SynthesizeSpeechRequest.builder()
                .text(text)
                .textType(TextType.TEXT)
                .voiceId(properties.voiceId())
                .engine(properties.engine())
                .outputFormat(OutputFormat.MP3)
                .build();
        try {
            return pollyClient.synthesizeSpeechAsBytes(request).asByteArray();
        } catch (AwsServiceException e) {
            String errorCode = e.awsErrorDetails() == null ? null : e.awsErrorDetails().errorCode();
            if (e.statusCode() >= 400 && e.statusCode() < 500) {
                log.error("[Polly] 설정·권한·한도 오류 status={}, code={}", e.statusCode(), errorCode);
            } else {
                log.warn("[Polly] 서버 오류 status={}, code={}", e.statusCode(), errorCode);
            }
            throw new GeneralException(SpeechErrorCode.SYNTHESIS_UNAVAILABLE);
        } catch (SdkException e) {
            log.warn("[Polly] 호출 실패 {}: {}", e.getClass().getSimpleName(), e.getMessage());
            throw new GeneralException(SpeechErrorCode.SYNTHESIS_UNAVAILABLE);
        }
    }
}
