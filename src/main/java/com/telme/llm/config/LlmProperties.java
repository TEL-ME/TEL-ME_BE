package com.telme.llm.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "llm")
public record LlmProperties(
        String model,
        Duration connectTimeout,
        Duration readTimeout
) {
}
