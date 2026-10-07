package com.telme.store.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExpiringLruCacheTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-09-30T00:00:00Z"));
    private final AtomicInteger loads = new AtomicInteger();

    @Test
    @DisplayName("유효 기간 안에서는 같은 키를 다시 불러오지 않는다")
    void 유효_기간_안에서는_재사용() {
        ExpiringLruCache<String> cache = new ExpiringLruCache<>(clock, Duration.ofHours(24), 10);

        assertThat(cache.getOrLoad("강남구", this::load)).isEqualTo("value-1");
        clock.advance(Duration.ofHours(23));
        assertThat(cache.getOrLoad("강남구", this::load)).isEqualTo("value-1");
        assertThat(loads).hasValue(1);
    }

    @Test
    @DisplayName("유효 기간이 지나면 다시 불러온다")
    void 기간이_지나면_다시_불러옴() {
        ExpiringLruCache<String> cache = new ExpiringLruCache<>(clock, Duration.ofHours(24), 10);

        cache.getOrLoad("강남구", this::load);
        clock.advance(Duration.ofHours(24));

        assertThat(cache.getOrLoad("강남구", this::load)).isEqualTo("value-2");
        assertThat(loads).hasValue(2);
    }

    @Test
    @DisplayName("불러오다 실패하면 저장하지 않아 다음 호출에서 다시 시도한다")
    void 실패는_저장하지_않음() {
        ExpiringLruCache<String> cache = new ExpiringLruCache<>(clock, Duration.ofHours(24), 10);

        assertThatThrownBy(() -> cache.getOrLoad("강남구", () -> {
            throw new IllegalStateException("timeout");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(cache.getOrLoad("강남구", this::load)).isEqualTo("value-1");
    }

    @Test
    @DisplayName("최대 개수를 넘으면 가장 오래 안 쓴 키부터 지운다")
    void 최대_개수를_넘으면_오래된_것부터_삭제() {
        ExpiringLruCache<String> cache = new ExpiringLruCache<>(clock, Duration.ofHours(24), 2);

        cache.getOrLoad("A", this::load);
        cache.getOrLoad("B", this::load);
        cache.getOrLoad("A", this::load);
        cache.getOrLoad("C", this::load);
        assertThat(loads).hasValue(3);

        cache.getOrLoad("A", this::load);
        assertThat(loads).hasValue(3);
        cache.getOrLoad("B", this::load);
        assertThat(loads).hasValue(4);
    }

    private String load() {
        return "value-" + loads.incrementAndGet();
    }

    private static class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            this.instant = this.instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
