package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telme.chat.entity.ChatExecution;
import com.telme.chat.entity.ChatMessage;
import org.junit.jupiter.api.Test;

class SseChatStreamPublisherTest {

    private static final Long EXECUTION_ID = 10L;

    private final ChatEmitterRegistry emitterRegistry = mock(ChatEmitterRegistry.class);
    private final SseChatStreamPublisher publisher = new SseChatStreamPublisher(emitterRegistry);

    @Test
    void keepsGeneratingWhenNoOneHasSubscribedYet() {
        when(emitterRegistry.isRegistered(EXECUTION_ID)).thenReturn(false);

        assertThat(publisher.publishToken(EXECUTION_ID, "로밍은 ")).isTrue();
        verify(emitterRegistry, never()).sendEvent(any(), anyString(), any());
    }

    @Test
    void sendsTokenToSubscriber() {
        when(emitterRegistry.isRegistered(EXECUTION_ID)).thenReturn(true);
        when(emitterRegistry.sendEvent(EXECUTION_ID, SseChatStreamPublisher.TOKEN, "로밍은 ")).thenReturn(true);

        assertThat(publisher.publishToken(EXECUTION_ID, "로밍은 ")).isTrue();
    }

    @Test
    void reportsLeaveWhenSendingToSubscriberFails() {
        when(emitterRegistry.isRegistered(EXECUTION_ID)).thenReturn(true);
        when(emitterRegistry.sendEvent(EXECUTION_ID, SseChatStreamPublisher.TOKEN, "로밍은 ")).thenReturn(false);

        assertThat(publisher.publishToken(EXECUTION_ID, "로밍은 ")).isFalse();
    }

    @Test
    void reportsLeaveWhenSubscriberDisappearsAfterConnecting() {
        when(emitterRegistry.isRegistered(EXECUTION_ID)).thenReturn(true, false);
        when(emitterRegistry.sendEvent(EXECUTION_ID, SseChatStreamPublisher.TOKEN, "로밍은 ")).thenReturn(true);

        publisher.publishToken(EXECUTION_ID, "로밍은 ");

        assertThat(publisher.publishToken(EXECUTION_ID, "앱에서")).isFalse();
    }

    @Test
    void forgetsSubscriptionOnceExecutionEnds() {
        when(emitterRegistry.isRegistered(EXECUTION_ID)).thenReturn(true, false);
        when(emitterRegistry.sendEvent(EXECUTION_ID, SseChatStreamPublisher.TOKEN, "로밍은 ")).thenReturn(true);
        publisher.publishToken(EXECUTION_ID, "로밍은 ");

        ChatExecutionState state = new ChatExecutionState(20L, EXECUTION_ID, ChatExecution.Status.COMPLETED, null, null);
        publisher.publishCompleted(EXECUTION_ID, state);

        verify(emitterRegistry).complete(EXECUTION_ID, state);
        assertThat(publisher.publishToken(EXECUTION_ID, "앱에서")).isTrue();
    }

    @Test
    void sendsStartAndRetryingEventsOnlyToSubscriber() {
        when(emitterRegistry.isRegistered(EXECUTION_ID)).thenReturn(true);
        ChatExecutionState state = new ChatExecutionState(20L, EXECUTION_ID, ChatExecution.Status.RUNNING, null, null);

        publisher.publishStarted(EXECUTION_ID, state);
        publisher.publishRetrying(EXECUTION_ID);

        verify(emitterRegistry).sendEvent(EXECUTION_ID, SseChatStreamPublisher.START, state);
        verify(emitterRegistry).sendEvent(
                EXECUTION_ID, SseChatStreamPublisher.STATUS, SseChatStreamPublisher.RETRYING);
    }

    @Test
    void closesSubscriptionWithFailure() {
        ChatFailure failure = new ChatFailure(ChatMessage.Status.CANCELLED, "USER_CANCELLED");

        publisher.publishFailed(EXECUTION_ID, failure);

        verify(emitterRegistry).fail(EXECUTION_ID, failure);
    }
}
