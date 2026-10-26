package com.telme.chat.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "chat.execution")
public record ChatExecutionProperties(
        @DefaultValue("5m") Duration runningTimeout
) {
}
