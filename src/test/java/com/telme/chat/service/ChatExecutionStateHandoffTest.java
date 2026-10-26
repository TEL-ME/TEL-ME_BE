package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.telme.chat.entity.ChatExecution;
import com.telme.chat.entity.ChatMessage;
import com.telme.chat.entity.ChatSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChatExecutionStateHandoffTest {

    @Test
    @DisplayName("출력 메시지의 상담원 연결 표시는 기본으로 꺼져 있다")
    void defaultsToFalse() {
        var output = new ChatOutputMessage(
                1L, 2L, 3L, 1, ChatMessage.MessageType.ANSWER, ChatMessage.Status.COMPLETED);

        assertThat(output.handoff()).isFalse();
    }

    @Test
    @DisplayName("저장된 메시지의 상담원 연결 표시를 출력 메시지로 옮긴다")
    void readsHandoffFromStoredMessage() {
        ChatSession session = mock(ChatSession.class);
        when(session.getSessionId()).thenReturn(1L);
        ChatExecution execution = mock(ChatExecution.class);
        when(execution.getExecutionId()).thenReturn(2L);
        ChatMessage message = mock(ChatMessage.class);
        when(message.getSession()).thenReturn(session);
        when(message.getMessageId()).thenReturn(3L);
        when(message.getSequenceNo()).thenReturn(1);
        when(message.getMessageType()).thenReturn(ChatMessage.MessageType.ANSWER);
        when(message.getStatus()).thenReturn(ChatMessage.Status.COMPLETED);
        when(message.isHandoff()).thenReturn(true);

        assertThat(ChatOutputMessage.of(execution, message).handoff()).isTrue();
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
