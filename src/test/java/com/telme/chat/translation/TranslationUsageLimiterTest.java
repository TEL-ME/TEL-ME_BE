package com.telme.chat.translation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.telme.chat.exception.TranslationErrorCode;
import com.telme.chat.service.ChatActor;
import com.telme.global.common.exception.GeneralException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TranslationUsageLimiterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-15T00:00:00Z"));
    private final TranslationUsageLimiter limiter = new TranslationUsageLimiter(clock);
    
    @Test
    @DisplayName("사용자별로 1분에 정해진 횟수까지 허용하고, 1분이 지나면 다시 허용한다")
    void 분당_요청_수를_막는다() {
        ChatActor member = new ChatActor(1L, null);
        for (int i = 0; i < TranslationUsageLimiter.REQUESTS_PER_MINUTE; i++) {
            limiter.acquire(member);
        }

        assertThatThrownBy(() -> limiter.acquire(member))
                .isInstanceOfSatisfying(GeneralException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(TranslationErrorCode.TOO_MANY_REQUESTS));
        assertThatCode(() -> limiter.acquire(new ChatActor(null, UUID.randomUUID()))).doesNotThrowAnyException();

        clock.advance(Duration.ofMinutes(1));
        assertThatCode(() -> limiter.acquire(member)).doesNotThrowAnyException();
    }
    
    private static final class MutableClock extends Clock {

        private Instant now;
        
        private MutableClock(Instant now) {
            this.now = now;
        }
        
        void advance(Duration duration) {
            now = now.plus(duration);
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
            return now;
        }
        
    }
}
