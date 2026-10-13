package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telme.chat.config.ChatEndSummaryProperties;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

class ChatSessionEndSummaryDispatcherTest {
    private final ChatSessionEndSummaryService service = mock(ChatSessionEndSummaryService.class);
    private final ChatSessionEndSummaryStore store = mock(ChatSessionEndSummaryStore.class);
    private final CountDownLatch entered = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);
    private final CountDownLatch processed = new CountDownLatch(21);
    private ChatSessionEndSummaryDispatcher dispatcher;
    private ThreadPoolTaskExecutor executor;

    @BeforeEach
    void 실행기를_준비한다() {
        var properties = new ChatEndSummaryProperties(true, true, true, 3072, 768, 400, 8, 2,
                Duration.ofMinutes(5), Duration.ofMinutes(15));
        dispatcher = new ChatSessionEndSummaryDispatcher(service, store, properties);
        dispatcher.initialize();
        executor = (ThreadPoolTaskExecutor) ReflectionTestUtils.getField(dispatcher, "executor");
        doAnswer(invocation -> {
            entered.countDown();
            release.await(10, TimeUnit.SECONDS);
            processed.countDown();
            return null;
        }).when(service).process(anyLong());
    }

    @AfterEach
    void 실행기를_종료한다() throws Exception {
        release.countDown();
        executor.getThreadPoolExecutor().shutdown();
        executor.getThreadPoolExecutor().awaitTermination(3, TimeUnit.SECONDS);
        dispatcher.shutdown();
    }

    @Test
    @DisplayName("반복 복구가 이미 대기 중인 작업으로 큐를 채우지 않는다")
    void 대기_작업의_중복_접수를_막는다() throws Exception {
        dispatcher.dispatch(new ChatSessionEndSummaryService.Requested(1));
        assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
        for (long id = 2; id <= 5; id++) {
            dispatcher.dispatch(new ChatSessionEndSummaryService.Requested(id));
        }
        when(store.candidates()).thenReturn(List.of(2L, 3L, 4L, 5L));

        for (int round = 0; round < 5; round++) {
            dispatcher.recover();
        }

        assertThat(executor.getQueueSize()).isEqualTo(4);
    }

    @Test
    @DisplayName("종료 이벤트와 복구가 동시에 와도 같은 작업은 한 번만 제출한다")
    void 동시_접수도_한_번만_실행한다() throws Exception {
        when(store.candidates()).thenReturn(List.of(7L));
        try (var callers = Executors.newFixedThreadPool(8)) {
            for (int index = 0; index < 40; index++) {
                callers.submit(() -> dispatcher.dispatch(new ChatSessionEndSummaryService.Requested(7)));
                callers.submit(dispatcher::recover);
            }
        }

        assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(executor.getQueueSize()).isZero();
        verify(service).process(7);
    }

    @Test
    @DisplayName("작업 실패 후 중복 방지 표식을 지워 다시 복구할 수 있다")
    void 실패한_작업도_다시_접수할_수_있다() throws Exception {
        doAnswer(invocation -> {
            throw new IllegalStateException("검증용 실패");
        }).when(service).process(7);
        dispatcher.dispatch(new ChatSessionEndSummaryService.Requested(7));
        barrier();

        dispatcher.dispatch(new ChatSessionEndSummaryService.Requested(7));
        barrier();

        verify(service, times(2)).process(7);
    }

    @Test
    @DisplayName("큐가 거절한 작업의 표식을 해제해 다음 복구에서 접수할 수 있다")
    void 접수_거절이_영구_차단으로_남지_않는다() throws Exception {
        dispatcher.dispatch(new ChatSessionEndSummaryService.Requested(1));
        assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
        for (long id = 2; id <= 22; id++) {
            dispatcher.dispatch(new ChatSessionEndSummaryService.Requested(id));
        }
        @SuppressWarnings("unchecked")
        Set<Long> submitted = (Set<Long>) ReflectionTestUtils.getField(dispatcher, "submitted");
        assertThat(submitted).doesNotContain(22L);
        release.countDown();
        assertThat(processed.await(2, TimeUnit.SECONDS)).isTrue();
        barrier();
        when(store.candidates()).thenReturn(List.of(22L));

        dispatcher.recover();
        barrier();

        verify(service).process(22);
    }

    private void barrier() throws Exception {
        executor.getThreadPoolExecutor().submit(() -> {}).get(2, TimeUnit.SECONDS);
    }
}
