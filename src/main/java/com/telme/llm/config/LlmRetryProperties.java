package com.telme.llm.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "llm.retry")
public record LlmRetryProperties(
        int maxAttempts,
        Duration waitDuration
) {
}
