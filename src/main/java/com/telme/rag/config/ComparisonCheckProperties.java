package com.telme.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "rag.comparison-check")
public record ComparisonCheckProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("1000") int maxTokens,
        @DefaultValue("4") int maxSentences,
        @DefaultValue("12000") int maxInputChars
) {
    public ComparisonCheckProperties {
        if (maxTokens < 1 || maxSentences < 1 || maxSentences > 8 || maxInputChars < 1) {
            throw new IllegalArgumentException("비교 근거 판정의 토큰·입력 한도는 양수, 문장 한도는 1~8이어야 합니다.");
        }
    }
}
