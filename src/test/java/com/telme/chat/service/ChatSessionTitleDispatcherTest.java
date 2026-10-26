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

class ChatSessionTitleDispatcherTest {

    @Test
    void dispatchesTitleGenerationOutsideCallerThread() {
        ChatSessionTitleService service = mock(ChatSessionTitleService.class);
        ChatSessionTitleRequested request = new ChatSessionTitleRequested(1L, 2L, "요금제 알려줘");
        AtomicReference<String> threadName = new AtomicReference<>();
        doAnswer(invocation -> {
            threadName.set(Thread.currentThread().getName());
            return true;
        }).when(service).generateIfMissing(request);
        ChatBackgroundTaskExecutor executor = new ChatBackgroundTaskExecutor();
        ChatSessionTitleDispatcher dispatcher = new ChatSessionTitleDispatcher(service, executor);

        try {
            dispatcher.dispatch(request);

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                    assertThat(threadName.get()).startsWith(ChatBackgroundTaskExecutor.THREAD_NAME_PREFIX));
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void isolatesTitleGenerationFailureFromCaller() {
        ChatSessionTitleService service = mock(ChatSessionTitleService.class);
        ChatSessionTitleRequested request = new ChatSessionTitleRequested(1L, 2L, "요금제 알려줘");
        doThrow(new IllegalStateException("title failed"))
                .when(service).generateIfMissing(request);
        ChatBackgroundTaskExecutor executor = new ChatBackgroundTaskExecutor();
        ChatSessionTitleDispatcher dispatcher = new ChatSessionTitleDispatcher(service, executor);

        try {
            dispatcher.dispatch(request);

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                    verify(service).generateIfMissing(request));
        } finally {
            executor.shutdown();
        }
    }
}
