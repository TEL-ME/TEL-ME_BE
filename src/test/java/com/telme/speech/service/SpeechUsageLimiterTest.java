package com.telme.speech.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.telme.chat.service.ChatActor;
import com.telme.global.common.exception.GeneralException;
import com.telme.speech.exception.SpeechErrorCode;
import com.telme.speech.service.SpeechTestSupport.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SpeechUsageLimiterTest {

    private static final ChatActor MEMBER = new ChatActor(1L, null);
    private static final ChatActor GUEST = new ChatActor(null, UUID.fromString("00000000-0000-0000-0000-000000000001"));

    private MutableClock clock;
    private SpeechUsageLimiter limiter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-10-12T05:00:00Z"));
        limiter = new SpeechUsageLimiter(clock, SpeechTestSupport.properties(2, 100, 1000));
    }

    @Test
    @DisplayName("한 사용자가 1분에 한도를 넘게 요청하면 막고, 1분이 지나면 다시 허용한다")
    void 사용자별_분당_한도() {
        limiter.acquire(MEMBER, 10);
        limiter.acquire(MEMBER, 10);

        assertThatThrownBy(() -> limiter.acquire(MEMBER, 10))
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getErrorCode())
                .isEqualTo(SpeechErrorCode.TOO_MANY_REQUESTS);
        assertThatCode(() -> limiter.acquire(GUEST, 10)).doesNotThrowAnyException();

        clock.advance(Duration.ofMinutes(1));
        assertThatCode(() -> limiter.acquire(MEMBER, 10)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("서비스 전체 하루 글자 수를 넘으면 막고, 한국 날짜가 바뀌면 다시 허용한다")
    void 하루_글자_수_한도() {
        limiter.acquire(MEMBER, 60);

        assertThatThrownBy(() -> limiter.acquire(GUEST, 41))
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getErrorCode())
                .isEqualTo(SpeechErrorCode.DAILY_LIMIT_EXCEEDED);

        clock.advance(Duration.ofHours(10));
        assertThatCode(() -> limiter.acquire(GUEST, 41)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("막힌 요청은 분당 횟수와 글자 수에 더하지 않는다")
    void 막힌_요청은_세지_않는다() {
        limiter.acquire(MEMBER, 90);
        assertThatThrownBy(() -> limiter.acquire(MEMBER, 20)).isInstanceOf(GeneralException.class);

        assertThatCode(() -> limiter.acquire(MEMBER, 10)).doesNotThrowAnyException();
    }
}
