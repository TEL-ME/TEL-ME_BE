package com.telme.chat.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

// bedrock: 종료 요약을 gpt-oss(Bedrock)로 만들고 실패하면 엑사원으로 대체한다(ChatEndSummaryConfiguration).
// 다른 호출과 같이 기본은 gpt-oss이고 false면 엑사원이다(docs/GPT_OSS_SWITCH.md)
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
        @DefaultValue("15m") Duration maxAge,
        @DefaultValue("true") boolean bedrock
) {
    public ChatEndSummaryProperties(boolean enabled, boolean dispatchEnabled, boolean schedulerEnabled,
            int maxInputTokens, int maxOutputTokens, int maxMessages, int maxChunks, int maxAttempts,
            Duration leaseDuration, Duration maxAge) {
        this(enabled, dispatchEnabled, schedulerEnabled, maxInputTokens, maxOutputTokens, maxMessages, maxChunks,
                maxAttempts, leaseDuration, maxAge, true);
    }

    @ConstructorBinding
    public ChatEndSummaryProperties {
        if (maxInputTokens < 256 || maxOutputTokens < 1 || maxMessages < 1 || maxChunks < 1
                || maxAttempts < 1 || leaseDuration.isNegative() || leaseDuration.isZero()
                || maxAge.compareTo(leaseDuration) < 0) {
            throw new IllegalArgumentException("종료 요약의 입력·출력·작업 한도를 확인해 주세요.");
        }
    }
}
