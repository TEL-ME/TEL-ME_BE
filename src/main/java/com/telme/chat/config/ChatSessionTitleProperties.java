package com.telme.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// bedrock: 제목을 gpt-oss(Bedrock)로 만들고 실패하면 엑사원으로 대체한다
// maxOutputTokens: gpt-oss는 답 앞의 내부 추론도 출력 한도를 써서 32로는 제목이 비거나 잘렸다(20개 중 7개 실패, 잘린 제목 포함)
@ConfigurationProperties(prefix = "chat.session-title")
public record ChatSessionTitleProperties(
        @DefaultValue("30") int maxLength,
        @DefaultValue("512") int maxOutputTokens,
        @DefaultValue("true") boolean bedrock
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
