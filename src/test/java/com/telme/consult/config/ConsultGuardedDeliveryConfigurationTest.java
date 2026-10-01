package com.telme.consult.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.telme.consult.service.ConsultChatEvents;
import com.telme.llm.exception.LlmStreamCancelledException;
import com.telme.llm.service.LlmStreamHandler;
import org.junit.jupiter.api.Test;

class ConsultGuardedDeliveryConfigurationTest {
    private final ConsultChatEvents events = mock(ConsultChatEvents.class);
    private final LlmStreamHandler output = mock(LlmStreamHandler.class);

    private LlmStreamHandler deferred() {
        when(events.stream(42L)).thenReturn(output);
        return new ConsultRagAnswerConfiguration()
                .consultAnswerStreamHandlerFactory(events).create(42L);
    }

    @Test
    void holdsAnswerTokensAndTerminalCallbacksUntilPersistence() {
        var stream = deferred();
        stream.onToken("검증된 답변");
        stream.onComplete();
        stream.onError(new IllegalStateException("오류"));

        verify(output).onProgress();
        verify(output, never()).onToken(anyString());
        verify(output, never()).onComplete();
        verify(output, never()).onError(any());
    }

    @Test
    void progressStillChecksCancellationWithoutSendingTokens() {
        var stream = deferred();
        doThrow(new LlmStreamCancelledException()).when(output).onProgress();

        assertThatThrownBy(stream::onProgress).isInstanceOf(LlmStreamCancelledException.class);
        verify(output, never()).onToken(anyString());
    }

    @Test
    void forwardsRetryStatusWithoutAnswerContent() {
        var stream = deferred();
        var failure = new IllegalStateException("재시도");
        stream.onRetry(2, failure);

        verify(output).onRetry(2, failure);
        verify(output, never()).onToken(anyString());
    }
}
