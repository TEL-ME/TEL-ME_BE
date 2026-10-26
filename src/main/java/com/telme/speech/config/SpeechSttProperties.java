package com.telme.speech.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// Transcribe 키는 Polly와 같은 AWS SDK 기본 자격 증명(AWS_ACCESS_KEY_ID 등 환경 변수)에서 읽는다
@ConfigurationProperties(prefix = "speech.stt")
public record SpeechSttProperties(
        @DefaultValue("ap-northeast-2") String region,
        @DefaultValue("ko-KR") String languageCode,
        @DefaultValue("30s") Duration maxDuration,
        @DefaultValue("20s") Duration timeout,
        @DefaultValue("5") int requestsPerMinute,
        @DefaultValue("60m") Duration dailyDurationLimit,
        // Transcribe 콘솔에서 만든 사용자 지정 어휘 이름. 같은 리전에 Ready 상태로 있어야 하고, 비우면 어휘 없이 인식한다
        String vocabularyName) {

    // 채팅 질문 한 번을 받는 기능이라 1분을 넘기면 본문 읽기 상한과 응답 대기 시간도 함께 다시 정해야 한다
    private static final Duration MAX_DURATION_LIMIT = Duration.ofMinutes(1);

    public SpeechSttProperties {
        if (maxDuration.isNegative() || maxDuration.isZero() || maxDuration.compareTo(MAX_DURATION_LIMIT) > 0) {
            throw new IllegalArgumentException("speech.stt.max-duration은 0초 초과 1분 이하여야 합니다.");
        }
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("speech.stt.timeout은 0초보다 커야 합니다.");
        }
        if (requestsPerMinute < 1 || dailyDurationLimit.toSeconds() < 1) {
            throw new IllegalArgumentException("speech.stt 사용량 한도는 1 이상이어야 합니다.");
        }
    }
}
