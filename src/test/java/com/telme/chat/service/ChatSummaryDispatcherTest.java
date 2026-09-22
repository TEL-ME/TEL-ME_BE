package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ChatSummaryDispatcherTest {

    @Test
    void dispatchesSummaryOutsideCallerThread() {
        ChatSummaryService service = mock(ChatSummaryService.class);
        AtomicReference<String> threadName = new AtomicReference<>();
        doAnswer(invocation -> {
            threadName.set(Thread.currentThread().getName());
            return true;
        }).when(service).summarizeIfNeeded(new ChatSummaryRequested(1L, 2L, 3));
        ChatSummaryDispatcher dispatcher = new ChatSummaryDispatcher(service);

        try {
            dispatcher.dispatch(new ChatSummaryRequested(1L, 2L, 3));

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                    assertThat(threadName.get()).startsWith(ChatSummaryDispatcher.THREAD_NAME_PREFIX));
        } finally {
            dispatcher.shutdown();
        }
    }

    @Test
    void isolatesSummaryFailureFromCaller() {
        ChatSummaryService service = mock(ChatSummaryService.class);
        ChatSummaryRequested request = new ChatSummaryRequested(1L, 2L, 3);
        doThrow(new IllegalStateException("summary failed"))
                .when(service).summarizeIfNeeded(request);
        ChatSummaryDispatcher dispatcher = new ChatSummaryDispatcher(service);

        try {
            dispatcher.dispatch(request);

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                    verify(service).summarizeIfNeeded(request));
        } finally {
            dispatcher.shutdown();
        }
    }
}
