package com.telme.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "chat.context")
public record ChatContextProperties(
        @DefaultValue("16") int maxHistoryMessages,
        @DefaultValue("4096") int maxHistoryTokens
) {

    public ChatContextProperties {
        if (maxHistoryMessages < 1) {
            throw new IllegalArgumentException("이전 대화 메시지 개수는 1 이상이어야 합니다.");
        }
        if (maxHistoryTokens < 1) {
            throw new IllegalArgumentException("이전 대화 최대 토큰 수는 1 이상이어야 합니다.");
        }
    }
}
