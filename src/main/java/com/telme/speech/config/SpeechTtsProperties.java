package com.telme.speech.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

// Polly 키는 여기 두지 않고 AWS SDK 기본 자격 증명(AWS_ACCESS_KEY_ID 등 환경 변수)에서 읽는다
@ConfigurationProperties(prefix = "speech.tts")
public record SpeechTtsProperties(
        @DefaultValue("ap-northeast-2") String region,
        @DefaultValue("Seoyeon") String voiceId,
        @DefaultValue("neural") String engine,
        @DefaultValue("3000") int maxTextLength,
        @DefaultValue("10s") Duration timeout,
        @DefaultValue("10") int requestsPerMinute,
        @DefaultValue("100000") int dailyCharacterLimit,
        @DefaultValue("20MB") DataSize cacheMaxSize,
        @DefaultValue("1h") Duration cacheTtl) {

    private static final int POLLY_MAX_TEXT_LENGTH = 3000;

    public SpeechTtsProperties {
        if (maxTextLength < 1 || maxTextLength > POLLY_MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("speech.tts.max-text-length는 1~3000 사이여야 합니다.");
        }
        if (requestsPerMinute < 1 || dailyCharacterLimit < 1) {
            throw new IllegalArgumentException("speech.tts 사용량 한도는 1 이상이어야 합니다.");
        }
    }
}
