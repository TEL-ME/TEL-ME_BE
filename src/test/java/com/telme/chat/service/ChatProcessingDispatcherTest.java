package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

class ChatProcessingDispatcherTest {

    private final AtomicLong now = new AtomicLong();
    private final ChatResponseTimer timer = new ChatResponseTimer(now::get);
    private final ChatProcessingPort port = mock(ChatProcessingPort.class);
    private final ChatExecutionService executions = mock(ChatExecutionService.class);
    private ThreadPoolTaskExecutor executor;
    private ChatProcessingDispatcher dispatcher;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(10);
        executor.initialize();
        ObjectProvider<ChatProcessingPort> ports = mock(ObjectProvider.class);
        when(ports.getIfAvailable()).thenReturn(port);
        dispatcher = new ChatProcessingDispatcher(
                ports, executions, timer, mock(PlatformTransactionManager.class), executor);
    }

    @AfterEach
    void tearDown() {
        executor.shutdown();
    }

    @Test
    @DisplayName("처리 중인 실행을 다시 받으면 실행기에 넘기지 않고, 처음 시작 값과 상태를 건드리지 않는다")
    void 처리_중인_실행의_중복_요청은_건너뛴다() throws Exception {
        CountDownLatch processing = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        blockUntil(processing, finish);

        dispatcher.dispatch(command(42L, new ChatResponseStopwatch(0)));
        assertThat(processing.await(5, TimeUnit.SECONDS)).isTrue();
        dispatcher.dispatch(command(42L, new ChatResponseStopwatch(TimeUnit.MILLISECONDS.toNanos(900))));
        now.set(TimeUnit.MILLISECONDS.toNanos(1_000));

        // 중복 요청이 돌아간 뒤에도 원래 작업의 측정값이 남아 있다
        assertThat(timer.findElapsedMillis(42L)).isEqualTo(1_000);
        finish.countDown();
        await().atMost(Duration.ofSeconds(5)).until(() -> timer.countTracked() == 0);
        verify(port, times(1)).request(any());
        verify(executions, never()).fail(any(), any());
    }

    @Test
    @DisplayName("같은 실행을 동시에 여러 번 넘겨도 처리는 한 번만 실행된다")
    void 동시_중복_요청도_처리는_한_번이다() throws Exception {
        CountDownLatch processing = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        blockUntil(processing, finish);
        int callers = 6;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch returned = new CountDownLatch(callers);
        for (int i = 0; i < callers; i++) {
            Thread.ofPlatform().start(() -> {
                try {
                    start.await();
                    dispatcher.dispatch(command(42L, new ChatResponseStopwatch(now.get())));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } finally {
                    returned.countDown();
                }
            });
        }

        start.countDown();
        assertThat(returned.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(processing.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(timer.countTracked()).isEqualTo(1);
        finish.countDown();

        await().atMost(Duration.ofSeconds(5)).until(() -> timer.countTracked() == 0);
        verify(port, times(1)).request(any());
        verify(executions, never()).fail(any(), any());
    }

    @Test
    @DisplayName("시작 값이 없는 명령도 처리 중이면 중복 요청을 막고 측정값은 비워 둔다")
    void 시작_값이_없어도_중복_요청을_막는다() throws Exception {
        CountDownLatch processing = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        blockUntil(processing, finish);

        dispatcher.dispatch(command(42L, null));
        assertThat(processing.await(5, TimeUnit.SECONDS)).isTrue();
        dispatcher.dispatch(command(42L, null));

        assertThat(timer.findElapsedMillis(42L)).isNull();
        finish.countDown();
        await().atMost(Duration.ofSeconds(5)).until(() -> timer.countTracked() == 0);
        verify(port, times(1)).request(any());
    }

    @Test
    @DisplayName("서로 다른 실행은 각각 처리하고 끝나면 등록을 지운다")
    void 다른_실행은_각각_처리한다() {
        dispatcher.dispatch(command(1L, new ChatResponseStopwatch(0)));
        dispatcher.dispatch(command(2L, new ChatResponseStopwatch(0)));

        verify(port, timeout(5_000)).request(argThat(command -> command.executionId() == 1L));
        verify(port, timeout(5_000)).request(argThat(command -> command.executionId() == 2L));
        await().atMost(Duration.ofSeconds(5)).until(() -> timer.countTracked() == 0);
    }

    @Test
    @DisplayName("처리 중 예외가 나면 기존처럼 실패 처리하고 등록을 지운다")
    void 처리_예외_뒤_실패_처리하고_해제한다() {
        doThrow(new IllegalStateException("pipeline down")).when(port).request(any());

        dispatcher.dispatch(command(42L, new ChatResponseStopwatch(0)));

        verify(executions, timeout(5_000)).fail(eq(42L),
                argThat(failure -> ChatProcessingDispatcher.PROCESSING_ERROR.equals(failure.errorCode())));
        await().atMost(Duration.ofSeconds(5)).until(() -> timer.countTracked() == 0);
    }

    @Test
    @DisplayName("실행기가 거부하면 등록한 측정값으로 실패를 기록하고 등록을 지운다. 그 뒤 같은 실행은 다시 받을 수 있다")
    @SuppressWarnings("unchecked")
    void 제출_거부_뒤_실패_처리하고_해제한다() {
        ThreadPoolTaskExecutor rejecting = new ThreadPoolTaskExecutor() {
            @Override
            public void execute(Runnable task) {
                throw new TaskRejectedException("가득 참");
            }
        };
        ObjectProvider<ChatProcessingPort> ports = mock(ObjectProvider.class);
        ChatProcessingDispatcher rejectingDispatcher = new ChatProcessingDispatcher(
                ports, executions, timer, mock(PlatformTransactionManager.class), rejecting);
        Integer[] elapsedAtFailure = new Integer[1];
        doAnswer(invocation -> {
            elapsedAtFailure[0] = timer.findElapsedMillis(42L);
            return null;
        }).when(executions).fail(eq(42L), any());
        now.set(TimeUnit.MILLISECONDS.toNanos(70));

        rejectingDispatcher.dispatch(command(42L, new ChatResponseStopwatch(0)));

        verify(executions).fail(eq(42L),
                argThat(failure -> ChatProcessingDispatcher.DISPATCH_REJECTED.equals(failure.errorCode())));
        assertThat(elapsedAtFailure[0]).isEqualTo(70);
        assertThat(timer.countTracked()).isZero();
        verify(port, never()).request(any());

        dispatcher.dispatch(command(42L, new ChatResponseStopwatch(0)));
        verify(port, timeout(5_000)).request(any());
    }

    @Test
    @DisplayName("작업이 제출 호출 안에서 바로 끝나도 등록이 남거나 두 번 지워지지 않는다")
    @SuppressWarnings("unchecked")
    void 바로_끝나는_작업도_정리가_맞다() {
        ThreadPoolTaskExecutor inline = new ThreadPoolTaskExecutor() {
            @Override
            public void execute(Runnable task) {
                task.run();
            }
        };
        ObjectProvider<ChatProcessingPort> ports = mock(ObjectProvider.class);
        when(ports.getIfAvailable()).thenReturn(port);
        ChatProcessingDispatcher inlineDispatcher = new ChatProcessingDispatcher(
                ports, executions, timer, mock(PlatformTransactionManager.class), inline);

        inlineDispatcher.dispatch(command(42L, new ChatResponseStopwatch(0)));
        inlineDispatcher.dispatch(command(42L, new ChatResponseStopwatch(0)));

        // 앞 작업이 끝나 등록이 풀린 뒤라 두 번째도 처리된다(중복 여부는 DB의 RUNNING 확인이 맡는다)
        verify(port, times(2)).request(any());
        assertThat(timer.countTracked()).isZero();
        verify(executions, never()).fail(any(), any());
    }

    @Test
    @DisplayName("종료할 때 진행 중인 턴은 끝나기를 기다리고, 시작 전인 턴은 바로 서버 종료 실패로 기록한다")
    void 종료_때_진행_중은_기다리고_시작_전은_실패_기록한다() throws Exception {
        ThreadPoolTaskExecutor single = singleThreadExecutor();
        ChatProcessingDispatcher shuttingDown = dispatcher(single, Duration.ofSeconds(10));
        CountDownLatch processing = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        blockUntil(processing, finish);

        shuttingDown.dispatch(command(1L, null));
        assertThat(processing.await(5, TimeUnit.SECONDS)).isTrue();
        shuttingDown.dispatch(command(2L, null));
        Thread shutdown = Thread.ofPlatform().start(shuttingDown::shutdown);

        verify(executions, timeout(5_000)).fail(eq(2L), argThat(isShutdownFailure()));
        // 진행 중인 턴은 아직 끝나지 않았으므로 건드리지 않고 종료도 기다린다
        verify(executions, never()).fail(eq(1L), any());
        assertThat(shutdown.isAlive()).isTrue();

        finish.countDown();
        shutdown.join(5_000);
        assertThat(shutdown.isAlive()).isFalse();
        verify(port, times(1)).request(any());
        verify(executions, never()).fail(eq(1L), any());
        assertThat(timer.countTracked()).isZero();
    }

    @Test
    @DisplayName("종료 대기 시간 안에 끝나지 않은 턴은 서버 종료 실패로 기록하고 끊는다")
    void 대기_시간이_지나면_남은_턴을_실패_기록하고_끊는다() throws Exception {
        ThreadPoolTaskExecutor single = singleThreadExecutor();
        ChatProcessingDispatcher shuttingDown = dispatcher(single, Duration.ofMillis(200));
        CountDownLatch processing = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        doAnswer(invocation -> {
            processing.countDown();
            try {
                new CountDownLatch(1).await(30, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                interrupted.countDown();
                Thread.currentThread().interrupt();
            }
            return null;
        }).when(port).request(any());

        shuttingDown.dispatch(command(1L, null));
        assertThat(processing.await(5, TimeUnit.SECONDS)).isTrue();
        shuttingDown.shutdown();

        verify(executions).fail(eq(1L), argThat(isShutdownFailure()));
        assertThat(interrupted.await(5, TimeUnit.SECONDS)).isTrue();
        await().atMost(Duration.ofSeconds(5)).until(() -> timer.countTracked() == 0);
    }

    @Test
    @DisplayName("종료 중 실패 기록이 DB 오류로 던져져도 나머지 턴을 정리하고 종료를 마친다")
    void 실패_기록이_던져도_나머지_턴을_정리한다() throws Exception {
        ThreadPoolTaskExecutor single = singleThreadExecutor();
        ChatProcessingDispatcher shuttingDown = dispatcher(single, Duration.ofMillis(200));
        CountDownLatch processing = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        blockUntil(processing, finish);
        doThrow(new IllegalStateException("db down")).when(executions).fail(eq(2L), any());

        shuttingDown.dispatch(command(1L, null));
        assertThat(processing.await(5, TimeUnit.SECONDS)).isTrue();
        shuttingDown.dispatch(command(2L, null));
        shuttingDown.dispatch(command(3L, null));
        shuttingDown.shutdown();

        verify(executions).fail(eq(2L), argThat(isShutdownFailure()));
        verify(executions).fail(eq(3L), argThat(isShutdownFailure()));
        verify(executions).fail(eq(1L), argThat(isShutdownFailure()));
        finish.countDown();
    }

    @Test
    @DisplayName("진행 중인 턴이 없으면 기다리지 않고 바로 종료한다")
    void 진행_중인_턴이_없으면_바로_종료한다() {
        ChatProcessingDispatcher idle = dispatcher(singleThreadExecutor(), Duration.ofSeconds(10));

        long started = System.nanoTime();
        idle.shutdown();

        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(2));
        verify(executions, never()).fail(any(), any());
    }

    private ThreadPoolTaskExecutor singleThreadExecutor() {
        ThreadPoolTaskExecutor single = new ThreadPoolTaskExecutor();
        single.setCorePoolSize(1);
        single.setMaxPoolSize(1);
        single.setQueueCapacity(10);
        single.initialize();
        return single;
    }

    @SuppressWarnings("unchecked")
    private ChatProcessingDispatcher dispatcher(ThreadPoolTaskExecutor pool, Duration shutdownWait) {
        ObjectProvider<ChatProcessingPort> ports = mock(ObjectProvider.class);
        when(ports.getIfAvailable()).thenReturn(port);
        return new ChatProcessingDispatcher(
                ports, executions, timer, mock(PlatformTransactionManager.class), pool, shutdownWait);
    }

    private static org.mockito.ArgumentMatcher<ChatFailure> isShutdownFailure() {
        return failure -> ChatProcessingDispatcher.SERVER_SHUTDOWN.equals(failure.errorCode());
    }

    private void blockUntil(CountDownLatch processing, CountDownLatch finish) {
        doAnswer(invocation -> {
            processing.countDown();
            assertThat(finish.await(5, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(port).request(any());
    }

    private ChatProcessingCommand command(Long executionId, ChatResponseStopwatch stopwatch) {
        return new ChatProcessingCommand(executionId, 10L, 20L, "질문", null, stopwatch);
    }
}
