package com.telme.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "chat.session-title")
public record ChatSessionTitleProperties(
        @DefaultValue("30") int maxLength,
        @DefaultValue("32") int maxOutputTokens
) {

    private static final int DATABASE_MAX_LENGTH = 100;

    public ChatSessionTitleProperties {
        if (maxLength < 1 || maxLength > DATABASE_MAX_LENGTH) {
            throw new IllegalArgumentException("자동 생성 세션 제목 길이는 1자 이상 100자 이하여야 합니다.");
        }
        if (maxOutputTokens < 1) {
            throw new IllegalArgumentException("세션 제목 생성 출력 토큰 수는 1 이상이어야 합니다.");
        }
    }
}
