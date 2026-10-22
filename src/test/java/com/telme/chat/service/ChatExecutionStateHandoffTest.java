package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.telme.chat.entity.ChatExecution;
import com.telme.chat.entity.ChatMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChatExecutionStateHandoffTest {

    private static ChatExecutionState completed() {
        return new ChatExecutionState(1L, 2L, ChatExecution.Status.COMPLETED, null,
                new ChatOutputMessage(1L, 2L, 3L, 1, ChatMessage.MessageType.ANSWER, ChatMessage.Status.COMPLETED));
    }

    @Test
    @DisplayName("상담원 연결 표시는 기본으로 꺼져 있다")
    void defaultsToFalse() {
        assertThat(completed().outputMessage().handoff()).isFalse();
    }

    @Test
    @DisplayName("상담원 연결 표시를 켜면 출력 메시지에만 반영된다")
    void withHandoffSetsOutputMessage() {
        ChatExecutionState state = completed().withHandoff(true);

        assertThat(state.outputMessage().handoff()).isTrue();
        assertThat(state.executionId()).isEqualTo(2L);
        assertThat(state.status()).isEqualTo(ChatExecution.Status.COMPLETED);
    }

    @Test
    @DisplayName("출력 메시지가 없으면 그대로 돌려준다")
    void withoutOutputMessage() {
        ChatExecutionState state = new ChatExecutionState(1L, 2L, ChatExecution.Status.COMPLETED, null, null);

        assertThat(state.withHandoff(true)).isSameAs(state);
    }

    @Test
    @DisplayName("답변의 상담원 연결 표시는 기본으로 꺼져 있고 withHandoff로 켠다")
    void answerHandoff() {
        ChatAnswer answer = new ChatAnswer(ChatMessage.MessageType.ANSWER, "답변", null, null, null);

        assertThat(answer.handoff()).isFalse();
        assertThat(answer.withHandoff(true).handoff()).isTrue();
        assertThat(ChatPersona.decorate("질문", answer.withHandoff(true)).handoff()).isTrue();
    }
}
