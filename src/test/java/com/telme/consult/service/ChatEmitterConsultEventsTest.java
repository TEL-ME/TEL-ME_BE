package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;

import com.telme.chat.entity.ChatMessage;
import com.telme.chat.service.ChatEmitterRegistry;
import com.telme.chat.service.ChatExecutionState;
import com.telme.chat.service.ChatFailure;
import com.telme.chat.service.ChatOutputMessage;
import com.telme.llm.exception.LlmStreamCancelledException;

import java.util.ArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChatEmitterConsultEventsTest {
    private final ChatEmitterRegistry emitters = mock(ChatEmitterRegistry.class);
    private final ChatEmitterConsultEvents events = new ChatEmitterConsultEvents(emitters);

    @BeforeEach
    void runTerminalLockAction() {
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(1).run();
            return null;
        }).when(emitters).withTerminalLock(anyLong(), any());
    }

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
    @Test
    void savesUndeliveredNotificationOnlyWhenTerminalEventIsNotDelivered() {
        var saved = new ArrayList<String>();
        var withNotifications = new ChatEmitterConsultEvents(
                emitters, (executionId, type) -> saved.add(executionId + ":" + type));
        var failed = new ChatFailure(ChatMessage.Status.FAILED, "AI_PROCESSING_ERROR");
        var timeout = new ChatFailure(ChatMessage.Status.TIMEOUT, "TIMEOUT");
        var cancelled = new ChatFailure(ChatMessage.Status.CANCELLED, "CLIENT_DISCONNECTED");
        when(emitters.complete(eq(1L), any())).thenReturn(true);
        when(emitters.complete(eq(2L), any())).thenReturn(false);
        when(emitters.fail(eq(3L), any())).thenReturn(false);
        when(emitters.fail(eq(4L), any())).thenReturn(false);
        when(emitters.fail(eq(5L), any())).thenReturn(false);
        // 6번은 늦게 구독한 화면에 이미 전한 실행
        when(emitters.complete(eq(6L), any())).thenReturn(false);
        when(emitters.consumeDeliveredLate(6L)).thenReturn(true);

        withNotifications.completed(1L, null);
        withNotifications.completed(2L, null);
        withNotifications.failed(3L, failed);
        withNotifications.failed(4L, timeout);
        withNotifications.failed(5L, cancelled);
        withNotifications.completed(6L, null);

        assertThat(saved)
                .containsExactly("2:ANSWER_ARRIVED", "3:ANSWER_FAILED", "4:ANSWER_FAILED");
    }

    @Test
    void notificationFailureDoesNotBreakAnswerCompletion() {
        var withBrokenNotifications = new ChatEmitterConsultEvents(emitters, (executionId, type) -> {
            throw new IllegalStateException("db down");
        });
        when(emitters.complete(eq(2L), any())).thenReturn(false);

        withBrokenNotifications.completed(2L, null);

        verify(emitters).complete(2L, null);
    }
}
