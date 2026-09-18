package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.telme.chat.entity.ChatExecution;
import com.telme.chat.entity.ChatMessage;
import org.junit.jupiter.api.Test;

class ChatExecutionCommandTest {

    @Test
    void answerAllowsOnlyAnswerAndStoreResult() {
        assertThatThrownBy(() -> new ChatAnswer(ChatMessage.MessageType.CLARIFICATION, "질문", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChatAnswer(ChatMessage.MessageType.ERROR, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void failureMapsMessageStatusToExecutionStatus() {
        assertThat(new ChatFailure(ChatMessage.Status.FAILED, "MODEL_ERROR").executionStatus())
                .isEqualTo(ChatExecution.Status.FAILED);
        assertThat(new ChatFailure(ChatMessage.Status.TIMEOUT, "LLM_TIMEOUT").executionStatus())
                .isEqualTo(ChatExecution.Status.FAILED);
        assertThat(new ChatFailure(ChatMessage.Status.CANCELLED, "USER_CANCELLED").executionStatus())
                .isEqualTo(ChatExecution.Status.CANCELLED);
    }

    @Test
    void failureRejectsNonTerminalStatusAndInvalidErrorCode() {
        assertThatThrownBy(() -> new ChatFailure(ChatMessage.Status.COMPLETED, "X"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChatFailure(ChatMessage.Status.GENERATING, "X"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChatFailure(ChatMessage.Status.FAILED, " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChatFailure(ChatMessage.Status.FAILED, "E".repeat(51)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
