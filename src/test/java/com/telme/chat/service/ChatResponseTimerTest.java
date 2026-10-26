package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChatResponseTimerTest {

    private final AtomicLong now = new AtomicLong();
    private final ChatResponseTimer timer = new ChatResponseTimer(now::get);

    @Test
    @DisplayName("등록한 실행은 접수 때 시작한 값부터 지금까지를 ms로 잰다")
    void 접수부터_지금까지를_잰다() {
        now.set(TimeUnit.MILLISECONDS.toNanos(1_250));
        timer.register(command(1L, new ChatResponseStopwatch(TimeUnit.MILLISECONDS.toNanos(250))));

        assertThat(timer.findElapsedMillis(1L)).isEqualTo(1_000);
    }

    @Test
    @DisplayName("0ms는 유효한 측정값이다")
    void 영_ms도_측정값이다() {
        timer.register(command(1L, new ChatResponseStopwatch(now.get())));

        assertThat(timer.findElapsedMillis(1L)).isZero();
    }

    @Test
    @DisplayName("nanoTime 값이 넘어가도 차이로 경과 시간을 잰다")
    void 값이_넘어가도_차이로_잰다() {
        timer.register(command(1L, new ChatResponseStopwatch(Long.MAX_VALUE - TimeUnit.MILLISECONDS.toNanos(5))));
        now.set(Long.MIN_VALUE + TimeUnit.MILLISECONDS.toNanos(5) - 1);

        assertThat(timer.findElapsedMillis(1L)).isEqualTo(10);
    }

    @Test
    @DisplayName("시작 값이 없거나 등록하지 않은 실행, 해제한 실행은 측정 불가(null)다")
    void 시작_값이_없으면_null이다() {
        timer.register(command(1L, null));
        var second = timer.register(command(2L, new ChatResponseStopwatch(now.get())));
        timer.release(2L, second);

        assertThat(timer.findElapsedMillis(1L)).isNull();
        assertThat(timer.findElapsedMillis(2L)).isNull();
        assertThat(timer.findElapsedMillis(3L)).isNull();
        assertThat(timer.countTracked()).isEqualTo(1);
    }

    @Test
    @DisplayName("INTEGER 범위를 넘거나 거꾸로 된 경과 시간은 잘라 저장하지 않고 null로 둔다")
    void 범위_밖_경과_시간은_null이다() {
        long overIntegerMs = TimeUnit.MILLISECONDS.toNanos((long) Integer.MAX_VALUE + 1);
        timer.register(command(1L, new ChatResponseStopwatch(0)));
        timer.register(command(2L, new ChatResponseStopwatch(TimeUnit.MILLISECONDS.toNanos(10))));
        timer.register(command(3L, new ChatResponseStopwatch(0)));

        now.set(overIntegerMs);
        assertThat(timer.findElapsedMillis(1L)).isNull();
        now.set(0);
        assertThat(timer.findElapsedMillis(2L)).isNull();
        now.set(TimeUnit.MILLISECONDS.toNanos(Integer.MAX_VALUE));
        assertThat(timer.findElapsedMillis(3L)).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("이미 등록된 실행은 다시 등록되지 않고 처음 시작 값을 유지한다")
    void 중복_등록은_거부하고_처음_값을_유지한다() {
        var first = timer.register(command(42L, new ChatResponseStopwatch(0)));
        var duplicate = timer.register(command(42L, new ChatResponseStopwatch(TimeUnit.MILLISECONDS.toNanos(900))));
        now.set(TimeUnit.MILLISECONDS.toNanos(1_000));

        assertThat(first).isNotNull();
        assertThat(duplicate).isNull();
        assertThat(timer.findElapsedMillis(42L)).isEqualTo(1_000);
    }

    @Test
    @DisplayName("시작 값이 없는 실행도 처리 담당으로 등록돼 중복 등록을 막는다")
    void 시작_값이_없어도_중복_등록을_막는다() {
        var first = timer.register(command(42L, null));
        var duplicate = timer.register(command(42L, null));

        assertThat(first).isNotNull();
        assertThat(duplicate).isNull();
        assertThat(timer.findElapsedMillis(42L)).isNull();
        assertThat(timer.countTracked()).isEqualTo(1);
    }

    @Test
    @DisplayName("끝난 작업의 해제는 같은 ID로 새로 등록된 작업을 지우지 않는다")
    void 오래된_해제는_새_등록을_지우지_않는다() {
        var old = timer.register(command(42L, new ChatResponseStopwatch(0)));
        timer.release(42L, old);
        var renewed = timer.register(command(42L, new ChatResponseStopwatch(0)));
        now.set(TimeUnit.MILLISECONDS.toNanos(30));

        timer.release(42L, old);

        assertThat(renewed).isNotNull();
        assertThat(timer.findElapsedMillis(42L)).isEqualTo(30);
        timer.release(42L, renewed);
        assertThat(timer.countTracked()).isZero();
    }

    @Test
    @DisplayName("같은 실행을 동시에 등록하면 하나만 성공한다")
    void 동시_등록은_하나만_성공한다() throws Exception {
        int threads = 8;
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<ChatResponseTimer.Registration>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return timer.register(command(42L, null));
                }));
            }
            start.countDown();
            int registered = 0;
            for (var result : results) {
                if (result.get(5, TimeUnit.SECONDS) != null) {
                    registered++;
                }
            }

            assertThat(registered).isEqualTo(1);
            assertThat(timer.countTracked()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    private ChatProcessingCommand command(Long executionId, ChatResponseStopwatch stopwatch) {
        return new ChatProcessingCommand(executionId, 10L, 20L, "질문", null, stopwatch);
    }
}
