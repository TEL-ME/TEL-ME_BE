package com.telme.llm.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// Bedrock 순간 장애 자동 재시도. maxAttempts는 첫 호출 포함 횟수
@ConfigurationProperties(prefix = "llm.bedrock.retry")
public record BedrockRetryProperties(
        @DefaultValue("2") int maxAttempts,
        @DefaultValue("2s") Duration waitDuration
) {

    public LlmRetryProperties toRetryProperties() {
        return new LlmRetryProperties(maxAttempts, waitDuration);
    }
}
