package com.telme.chat.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ChatContextPropertiesTest {

    @Test
    void acceptsPositiveLimits() {
        ChatContextProperties properties = new ChatContextProperties(16, 4_096);

        assertThat(properties.maxHistoryMessages()).isEqualTo(16);
        assertThat(properties.maxHistoryTokens()).isEqualTo(4_096);
    }

    @Test
    void rejectsNonPositiveLimits() {
        assertThatThrownBy(() -> new ChatContextProperties(0, 100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이전 대화 메시지 개수는 1 이상이어야 합니다.");
        assertThatThrownBy(() -> new ChatContextProperties(10, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이전 대화 최대 토큰 수는 1 이상이어야 합니다.");
    }
}
