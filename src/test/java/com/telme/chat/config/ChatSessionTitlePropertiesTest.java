package com.telme.chat.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ChatSessionTitlePropertiesTest {

    @Test
    void acceptsConfiguredLimits() {
        ChatSessionTitleProperties properties = new ChatSessionTitleProperties(30, 512, true);

        assertThat(properties.maxLength()).isEqualTo(30);
        assertThat(properties.maxOutputTokens()).isEqualTo(512);
    }

    @Test
    void rejectsInvalidLimits() {
        assertThatThrownBy(() -> new ChatSessionTitleProperties(0, 512, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChatSessionTitleProperties(101, 512, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChatSessionTitleProperties(30, 0, true))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
