package com.telme.chat.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ChatSummaryPropertiesTest {

    @Test
    void acceptsPositiveLimits() {
        ChatSummaryProperties properties = new ChatSummaryProperties(16, 2_048, 8, 1_024, 8, 3_072, 512);

        assertThat(properties.triggerMessages()).isEqualTo(16);
        assertThat(properties.triggerTokens()).isEqualTo(2_048);
        assertThat(properties.retainedMessages()).isEqualTo(8);
        assertThat(properties.retainedTokens()).isEqualTo(1_024);
        assertThat(properties.maxBatchMessages()).isEqualTo(8);
        assertThat(properties.maxInputTokens()).isEqualTo(3_072);
        assertThat(properties.maxOutputTokens()).isEqualTo(512);
    }

    @Test
    void rejectsNonPositiveLimits() {
        assertThatThrownBy(() -> new ChatSummaryProperties(0, 2_048, 8, 1_024, 8, 3_072, 512))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChatSummaryProperties(16, 0, 8, 1_024, 8, 3_072, 512))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChatSummaryProperties(16, 2_048, 0, 1_024, 8, 3_072, 512))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChatSummaryProperties(16, 2_048, 16, 1_024, 8, 3_072, 512))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChatSummaryProperties(16, 2_048, 8, 0, 8, 3_072, 512))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChatSummaryProperties(16, 2_048, 8, 2_048, 8, 3_072, 512))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChatSummaryProperties(16, 2_048, 8, 1_024, 1, 3_072, 512))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChatSummaryProperties(16, 2_048, 8, 1_024, 8, 0, 512))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChatSummaryProperties(16, 2_048, 8, 1_024, 8, 3_072, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
