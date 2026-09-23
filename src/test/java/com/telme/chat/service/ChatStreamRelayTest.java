package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.telme.llm.exception.LlmStreamCancelledException;
import org.junit.jupiter.api.Test;

class ChatStreamRelayTest {

    private static final Long EXECUTION_ID = 10L;

    private final ChatStreamPublisher streamPublisher = mock(ChatStreamPublisher.class);
    private final ChatStreamRelay relay = new ChatStreamRelay(EXECUTION_ID, streamPublisher);

    @Test
    void relaysTokenToSubscriber() {
        when(streamPublisher.publishToken(EXECUTION_ID, "로밍은 ")).thenReturn(true);

        assertThatCode(() -> relay.onToken("로밍은 ")).doesNotThrowAnyException();
        verify(streamPublisher).publishToken(EXECUTION_ID, "로밍은 ");
    }

    @Test
    void stopsGenerationWhenSubscriberLeaves() {
        when(streamPublisher.publishToken(EXECUTION_ID, "로밍은 ")).thenReturn(false);

        assertThatThrownBy(() -> relay.onToken("로밍은 ")).isInstanceOf(LlmStreamCancelledException.class);
    }

    @Test
    void remembersOnlyTokensThatReachedSubscriber() {
        when(streamPublisher.publishToken(EXECUTION_ID, "로밍은 ")).thenReturn(true);
        when(streamPublisher.publishToken(EXECUTION_ID, "앱에서")).thenReturn(false);

        relay.onToken("로밍은 ");
        assertThatThrownBy(() -> relay.onToken("앱에서")).isInstanceOf(LlmStreamCancelledException.class);

        assertThat(relay.relayed()).isEqualTo("로밍은 ");
    }

    @Test
    void relaysRetryToSubscriber() {
        relay.onRetry(1, new IllegalStateException("timeout"));

        verify(streamPublisher).publishRetrying(EXECUTION_ID);
        verify(streamPublisher, never()).publishToken(any(), anyString());
    }

    @Test
    void leavesCompletionAndFailureToCaller() {
        relay.onComplete();
        relay.onError(new IllegalStateException("model error"));

        verifyNoInteractions(streamPublisher);
    }
}
