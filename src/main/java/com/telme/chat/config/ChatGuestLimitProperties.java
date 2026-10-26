package com.telme.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 게스트(비로그인)가 하루(한국 시간 0시 기준)에 보낼 수 있는 질문 수. 0이면 제한하지 않는다.
 * 회원은 제한하지 않는다. 쿠키를 지우면 새 게스트가 되어 우회되므로 로그인 유도와 기본 비용 억제용이다(TELME-213)
 */
@ConfigurationProperties(prefix = "chat.guest-limit")
public record ChatGuestLimitProperties(
        @DefaultValue("10") int dailyMessages
) {

    public ChatGuestLimitProperties {
        if (dailyMessages < 0) {
            throw new IllegalArgumentException("chat.guest-limit.daily-messages는 0 이상이어야 합니다.");
        }
    }
}
