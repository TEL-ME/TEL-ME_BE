package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ChatProcessingCommandTest {

    @Test
    void toStringExcludesQuestionContent() {
        ChatProcessingCommand command = new ChatProcessingCommand(1L, 2L, 3L, "010-1234-5678 명의 해지하고 싶어요");

        assertThat(command.toString())
                .isEqualTo("ChatProcessingCommand[executionId=1, sessionId=2, inputMessageId=3]")
                .doesNotContain("010-1234-5678");
    }
}
