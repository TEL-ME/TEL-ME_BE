package com.telme.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "chat.question-resolution")
public record ChatQuestionResolutionProperties(@DefaultValue("REGEX_GATED") Mode mode) {
    public enum Mode {
        REGEX_GATED,
        LLM_ALL
    }
}
