package com.telme.llm.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "llm.retry")
public record LlmRetryProperties(
        @DefaultValue("2") int maxAttempts,
        @DefaultValue("1s") Duration waitDuration
) {
}
