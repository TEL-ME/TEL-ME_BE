package com.telme.faq.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "embedding")
public record EmbeddingProperties(
        String model,
        int dimension,
        Duration connectTimeout,
        Duration searchReadTimeout,
        Duration batchReadTimeout
) {
}
