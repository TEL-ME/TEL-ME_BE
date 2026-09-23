package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class ChatEmitterRegistryTest {

    private final ChatEmitterRegistry registry = new ChatEmitterRegistry();

    @Test
    void registersAndReflectsPresence() {
        SseEmitter emitter = mock(SseEmitter.class);

        registry.register(157L, emitter);

        assertThat(registry.isRegistered(157L)).isTrue();
        assertThat(registry.isRegistered(999L)).isFalse();
    }

    @Test
    void sendEventDoesNothingWhenNoSubscriberIsRegistered() {
        // 구독 전에 토큰이 먼저 나오는 경우 — 조용히 무시되어야 한다(예외 없음)
        registry.sendEvent(157L, "token", "강남");
    }

    @Test
    void sendEventWritesToRegisteredEmitter() throws IOException {
        SseEmitter emitter = mock(SseEmitter.class);
        registry.register(157L, emitter);

        registry.sendEvent(157L, "token", "강남");

        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void sendEventRemovesEmitterWhenClientAlreadyDisconnected() throws IOException {
        SseEmitter emitter = mock(SseEmitter.class);
        doThrow(new IOException("연결 끊김")).when(emitter).send(any(SseEmitter.SseEventBuilder.class));
        registry.register(157L, emitter);

        registry.sendEvent(157L, "token", "강남");

        assertThat(registry.isRegistered(157L)).isFalse();
    }

    @Test
    void completeSendsCompleteEventAndUnregisters() throws IOException {
        SseEmitter emitter = mock(SseEmitter.class);
        registry.register(157L, emitter);

        registry.complete(157L, "결과 메시지");

        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
        verify(emitter).complete();
        assertThat(registry.isRegistered(157L)).isFalse();
    }

    @Test
    void completeIsNoOpWhenNoSubscriberIsRegistered() {
        // AI가 답변을 완료했는데 아직 아무도 구독하지 않은 경우 — 예외 없이 조용히 넘어가야 한다
        registry.complete(157L, "결과 메시지");
    }

    @Test
    void failSendsErrorEventAndUnregisters() throws IOException {
        SseEmitter emitter = mock(SseEmitter.class);
        registry.register(157L, emitter);

        registry.fail(157L, "AI_PROCESSING_ERROR");

        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
        verify(emitter).complete();
        assertThat(registry.isRegistered(157L)).isFalse();
    }

    @Test
    void sendTerminalNowSendsAndCompletesWithoutRegistering() throws IOException {
        SseEmitter emitter = mock(SseEmitter.class);

        registry.sendTerminalNow(emitter, "complete", "이미 끝난 실행");

        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
        verify(emitter).complete();
        assertThat(registry.isRegistered(157L)).isFalse();
    }

    @Test
    void timeoutCallbackUnregistersAndCompletesEmitter() {
        SseEmitter emitter = mock(SseEmitter.class);
        ArgumentCaptor<Runnable> onTimeout = ArgumentCaptor.forClass(Runnable.class);
        registry.register(157L, emitter);
        verify(emitter).onTimeout(onTimeout.capture());

        onTimeout.getValue().run();

        assertThat(registry.isRegistered(157L)).isFalse();
        verify(emitter).complete();
    }

    @Test
    void completionCallbackUnregistersEmitterWithoutCallingCompleteAgain() {
        SseEmitter emitter = mock(SseEmitter.class);
        ArgumentCaptor<Runnable> onCompletion = ArgumentCaptor.forClass(Runnable.class);
        registry.register(157L, emitter);
        verify(emitter).onCompletion(onCompletion.capture());

        onCompletion.getValue().run();

        assertThat(registry.isRegistered(157L)).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void errorCallbackUnregistersEmitter() {
        SseEmitter emitter = mock(SseEmitter.class);
        ArgumentCaptor<Consumer<Throwable>> onError = ArgumentCaptor.forClass(Consumer.class);
        registry.register(157L, emitter);
        verify(emitter).onError(onError.capture());

        onError.getValue().accept(new IOException("연결 끊김"));

        assertThat(registry.isRegistered(157L)).isFalse();
    }
}
