package com.telme.store.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.telme.global.common.exception.GeneralException;
import com.telme.store.config.StoreOriginWalkProperties;
import com.telme.store.dto.req.StoreOriginWalkRequest;
import com.telme.store.exception.StoreErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class StoreOriginWalkGuardTest {

    private final Clock clock = mock(Clock.class);
    private final Instant start = Instant.parse("2026-10-23T00:00:00Z");

    private StoreOriginWalkGuard guard(int distance, int requests) {
        when(clock.instant()).thenReturn(start);
        return new StoreOriginWalkGuard(new StoreOriginWalkProperties(distance, requests, List.of()), clock);
    }

    @Test
    void 서울_부산과_상한_바깥은_거절하고_안쪽과_동일_좌표는_허용한다() {
        StoreOriginWalkGuard guard = guard(3000, 30);
        assertThatThrownBy(() -> guard.checkDistance(new StoreOriginWalkRequest(37.5665, 126.9780, 35.1796, 129.0756)))
                .isInstanceOfSatisfying(GeneralException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(StoreErrorCode.ROUTE_DISTANCE_EXCEEDED));
        assertThatCode(() -> guard.checkDistance(north(2999))).doesNotThrowAnyException();
        assertThatThrownBy(() -> guard.checkDistance(north(3001))).isInstanceOf(GeneralException.class);
        assertThatCode(() -> guard.checkDistance(north(0))).doesNotThrowAnyException();
        assertThatThrownBy(() -> guard(100, 30).checkDistance(north(101))).isInstanceOf(GeneralException.class);
    }

    @Test
    void 경로_11개를_빠르게_선택해도_허용하고_31번째에만_제한한다() {
        StoreOriginWalkGuard guard = guard(3000, 30);
        for (int i = 0; i < 30; i++) {
            guard.acquire("192.0.2.1");
        }
        assertRateLimited(() -> guard.acquire("192.0.2.1"));
        assertThatCode(() -> guard.acquire("192.0.2.2")).doesNotThrowAnyException();
    }

    @Test
    void 분당_설정을_적용하고_60초가_되면_다시_허용한다() {
        StoreOriginWalkGuard guard = guard(3000, 1);
        guard.acquire("192.0.2.1");
        when(clock.instant()).thenReturn(start.plusSeconds(59));
        assertRateLimited(() -> guard.acquire("192.0.2.1"));
        when(clock.instant()).thenReturn(start.plusSeconds(60));
        assertThatCode(() -> guard.acquire("192.0.2.1")).doesNotThrowAnyException();
    }

    @Test
    void 동시_요청도_설정된_30회만_예약한다() throws Exception {
        StoreOriginWalkGuard guard = guard(3000, 30);
        AtomicInteger allowed = new AtomicInteger();
        AtomicInteger blocked = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(60);
        CountDownLatch fire = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = java.util.stream.IntStream.range(0, 60).mapToObj(i -> executor.submit(() -> {
                ready.countDown();
                fire.await();
                try {
                    guard.acquire("192.0.2.1");
                    allowed.incrementAndGet();
                } catch (GeneralException e) {
                    assertThat(e.getErrorCode()).isEqualTo(StoreErrorCode.ORIGIN_WALK_RATE_LIMIT_EXCEEDED);
                    blocked.incrementAndGet();
                }
                return null;
            })).toList();
            boolean allReady = ready.await(5, java.util.concurrent.TimeUnit.SECONDS);
            fire.countDown();
            assertThat(allReady).isTrue();
            for (var future : futures) {
                future.get(5, java.util.concurrent.TimeUnit.SECONDS);
            }
        }
        assertThat(allowed.get()).isEqualTo(30);
        assertThat(blocked.get()).isEqualTo(30);
    }

    @Test
    void 메모리_상한에서_활성_제한은_유지하고_만료된_항목을_정리한다() {
        StoreOriginWalkGuard guard = guard(3000, 1);
        for (int i = 0; i < 10_000; i++) {
            guard.acquire("client-" + i);
        }
        assertRateLimited(() -> guard.acquire("new-client"));
        assertRateLimited(() -> guard.acquire("client-0"));
        when(clock.instant()).thenReturn(start.plusSeconds(60));
        assertThatCode(() -> guard.acquire("new-client")).doesNotThrowAnyException();
    }

    private StoreOriginWalkRequest north(double meters) {
        return new StoreOriginWalkRequest(37.5, 127.0, 37.5 + Math.toDegrees(meters / 6_371_000), 127.0);
    }

    private void assertRateLimited(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(GeneralException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(StoreErrorCode.ORIGIN_WALK_RATE_LIMIT_EXCEEDED));
    }
}
