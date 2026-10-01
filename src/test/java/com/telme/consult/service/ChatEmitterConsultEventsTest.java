package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import com.telme.chat.entity.ChatMessage;
import com.telme.chat.service.ChatEmitterRegistry;
import com.telme.chat.service.ChatExecutionState;
import com.telme.chat.service.ChatFailure;
import com.telme.chat.service.ChatOutputMessage;
import com.telme.llm.exception.LlmStreamCancelledException;

import org.junit.jupiter.api.Test;

class ChatEmitterConsultEventsTest {
    private final ChatEmitterRegistry emitters = mock(ChatEmitterRegistry.class);
    private final ChatEmitterConsultEvents events = new ChatEmitterConsultEvents(emitters);

    @Test
    void sendsStartTokensRetryAndTerminalEvents() {
        var state =
                new ChatExecutionState(
                        1L,
                        2L,
                        com.telme.chat.entity.ChatExecution.Status.RUNNING,
                        null,
                        new ChatOutputMessage(
                                1L,
                                2L,
                                3L,
                                1,
                                ChatMessage.MessageType.ANSWER,
                                ChatMessage.Status.GENERATING));
        when(emitters.isRegistered(2L)).thenReturn(true);
        when(emitters.sendEvent(2L, "token", "답변")).thenReturn(true);

        events.started(state);
        var stream = events.stream(2L);
        stream.onToken("답변");
        stream.onRetry(2, new IllegalStateException("retry"));
        events.completed(2L, state);
        var failure = new ChatFailure(ChatMessage.Status.FAILED, "AI_PROCESSING_ERROR");
        events.failed(2L, failure);

        verify(emitters).sendEvent(2L, "start", state);
        verify(emitters).sendEvent(2L, "token", "답변");
        verify(emitters).sendEvent(2L, "status", "RETRYING");
        verify(emitters).complete(2L, state);
        verify(emitters).fail(2L, failure);
    }

    @Test
    void disconnectAfterSubscriptionCancelsModelGeneration() {
        when(emitters.isRegistered(2L)).thenReturn(true, false);
        var stream = events.stream(2L);

        assertThatThrownBy(() -> stream.onToken("답변"))
                .isInstanceOf(LlmStreamCancelledException.class);
    }

    @Test
    void progressChecksConnectionWithoutSendingAnSseEvent() {
        when(emitters.isRegistered(2L)).thenReturn(true);
        var stream = events.stream(2L);
        stream.onProgress();

        verify(emitters, never()).sendEvent(eq(2L), anyString(), any());
    }

    @Test
    void progressCancelsWhenSubscriptionDisconnects() {
        when(emitters.isRegistered(2L)).thenReturn(true, false);
        var stream = events.stream(2L);

        assertThatThrownBy(stream::onProgress).isInstanceOf(LlmStreamCancelledException.class);
        verify(emitters, never()).sendEvent(eq(2L), anyString(), any());
    }
}
