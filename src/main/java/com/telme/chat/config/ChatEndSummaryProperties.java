package com.telme.chat.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "chat.end-summary")
public record ChatEndSummaryProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("true") boolean dispatchEnabled,
        @DefaultValue("true") boolean schedulerEnabled,
        @DefaultValue("3072") int maxInputTokens,
        @DefaultValue("768") int maxOutputTokens,
        @DefaultValue("400") int maxMessages,
        @DefaultValue("8") int maxChunks,
        @DefaultValue("2") int maxAttempts,
        @DefaultValue("5m") Duration leaseDuration,
        @DefaultValue("15m") Duration maxAge
) {
    public ChatEndSummaryProperties {
        if (maxInputTokens < 256 || maxOutputTokens < 1 || maxMessages < 1 || maxChunks < 1
                || maxAttempts < 1 || leaseDuration.isNegative() || leaseDuration.isZero()
                || maxAge.compareTo(leaseDuration) < 0) {
            throw new IllegalArgumentException("종료 요약의 입력·출력·작업 한도를 확인해 주세요.");
        }
    }
}
