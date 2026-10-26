package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.telme.chat.entity.ChatMessage;
import org.junit.jupiter.api.Test;

class ChatTokenEstimatorTest {

    private final ChatTokenEstimator estimator = new ChatTokenEstimator();

    @Test
    void estimatesKoreanAndAsciiTextConservatively() {
        assertThat(estimator.estimateText("가나다")).isEqualTo(3);
        assertThat(estimator.estimateText("abcdefgh")).isEqualTo(2);
        assertThat(estimator.estimateText("abc 가나다")).isEqualTo(4);
    }

    @Test
    void includesMessageOverheadAndStoreResults() {
        ChatContextMessage message = new ChatContextMessage(
                1L,
                1,
                ChatMessage.Role.ASSISTANT,
                ChatMessage.MessageType.STORE_RESULT,
                null,
                "[{}]"
        );

        assertThat(estimator.estimate(message)).isEqualTo(8);
        assertThat(estimator.estimatePromptPart("가나다")).isEqualTo(7);
        assertThat(estimator.estimatePromptPart(" ")).isZero();
    }
}
