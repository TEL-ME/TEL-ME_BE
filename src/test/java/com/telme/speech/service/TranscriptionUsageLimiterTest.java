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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TranscriptionUsageLimiterTest {

    private static final ChatActor GUEST = new ChatActor(null, UUID.fromString("00000000-0000-0000-0000-000000000002"));

    @Test
    @DisplayName("짧은 녹음도 Transcribe 최소 과금 15초로 세어 하루 인식 시간에서 뺀다")
    void 최소_15초로_센다() {
        TranscriptionUsageLimiter limiter = new TranscriptionUsageLimiter(
                new MutableClock(Instant.parse("2026-10-13T03:00:00Z")),
                SpeechTestSupport.sttProperties(10, Duration.ofSeconds(45)), SpeechTestSupport.inMemoryDailyUsage());

        limiter.acquire(GUEST, Duration.ofSeconds(2));
        limiter.acquire(GUEST, Duration.ofMillis(14_100));
        limiter.acquire(GUEST, Duration.ofSeconds(1));

        assertThatThrownBy(() -> limiter.acquire(GUEST, Duration.ofSeconds(1)))
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getErrorCode())
                .isEqualTo(SpeechErrorCode.DAILY_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("15초보다 긴 녹음은 올림한 초만큼 센다")
    void 긴_녹음은_올림한_초() {
        TranscriptionUsageLimiter limiter = new TranscriptionUsageLimiter(
                new MutableClock(Instant.parse("2026-10-13T03:00:00Z")),
                SpeechTestSupport.sttProperties(10, Duration.ofSeconds(35)), SpeechTestSupport.inMemoryDailyUsage());

        limiter.acquire(GUEST, Duration.ofMillis(20_001));

        assertThatThrownBy(() -> limiter.acquire(GUEST, Duration.ofSeconds(1))).isInstanceOf(GeneralException.class);
        assertThatCode(() -> new TranscriptionUsageLimiter(new MutableClock(Instant.parse("2026-10-13T03:00:00Z")),
                SpeechTestSupport.sttProperties(10, Duration.ofSeconds(35)), SpeechTestSupport.inMemoryDailyUsage()).acquire(GUEST, Duration.ofSeconds(21)))
                .doesNotThrowAnyException();
    }
}
