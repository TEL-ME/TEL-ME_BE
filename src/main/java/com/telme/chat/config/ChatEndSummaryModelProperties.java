package com.telme.chat.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "chat.end-summary.model")
public record ChatEndSummaryModelProperties(
        @DefaultValue("ollama") Provider provider,
        @DefaultValue("us-east-1") String region,
        @DefaultValue("openai.gpt-oss-120b-1:0") String model,
        @DefaultValue("8s") Duration timeout,
        @DefaultValue("low") String reasoningEffort,
        @DefaultValue("exaone3.5:7.8b") String fallbackModel,
        @DefaultValue("5s") Duration fallbackConnectTimeout,
        @DefaultValue("60s") Duration fallbackReadTimeout,
        @DefaultValue("8192") int fallbackContextSize
) {
    public ChatEndSummaryModelProperties {
        if (provider == null || region == null || region.isBlank() || model == null || model.isBlank()
                || fallbackModel == null || fallbackModel.isBlank() || fallbackContextSize < 1
                || !isPositive(timeout) || !isPositive(fallbackConnectTimeout) || !isPositive(fallbackReadTimeout)) {
            throw new IllegalArgumentException("종료 요약 모델과 호출 시간 설정을 확인해 주세요.");
        }
    }

    private static boolean isPositive(Duration duration) {
        return duration != null && duration.compareTo(Duration.ofMillis(1)) >= 0
                && duration.compareTo(Duration.ofMillis(Integer.MAX_VALUE)) <= 0;
    }

    public enum Provider {
        OLLAMA, BEDROCK, PRIMARY
    }
}
