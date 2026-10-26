package com.telme.speech.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
import org.springframework.dao.QueryTimeoutException;
import org.springframework.transaction.CannotCreateTransactionException;

class SpeechUsageLimiterTest {

    private static final ChatActor MEMBER = new ChatActor(1L, null);
    private static final ChatActor GUEST = new ChatActor(null, UUID.fromString("00000000-0000-0000-0000-000000000001"));

    private MutableClock clock;
    private SpeechUsageLimiter limiter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-10-12T05:00:00Z"));
        limiter = new SpeechUsageLimiter(clock, SpeechTestSupport.properties(2, 100, 1000),
                SpeechTestSupport.inMemoryDailyUsage());
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

    @Test
    @DisplayName("하루 사용량을 확인하지 못하면 통과시키지 않고, 그 요청은 분당 횟수에 더하지 않는다")
    void 사용량_확인_실패는_막는다() {
        SpeechDailyUsageService failing = mock(SpeechDailyUsageService.class);
        when(failing.tryCharge(any(), any(), anyLong(), anyLong()))
                .thenThrow(new QueryTimeoutException("db down"))
                .thenThrow(new CannotCreateTransactionException("연결 실패"))
                .thenReturn(true);
        limiter = new SpeechUsageLimiter(clock, SpeechTestSupport.properties(1, 100, 1000), failing);

        for (int attempt = 0; attempt < 2; attempt++) {
            assertThatThrownBy(() -> limiter.acquire(MEMBER, 10))
                    .isInstanceOf(GeneralException.class)
                    .extracting(e -> ((GeneralException) e).getErrorCode())
                    .isEqualTo(SpeechErrorCode.USAGE_UNAVAILABLE);
        }

        assertThatCode(() -> limiter.acquire(MEMBER, 10)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("같은 하루 사용량 저장소를 쓰면 리미터를 새로 만들어도(재시작) 사용량이 이어진다")
    void 재시작해도_사용량이_이어진다() {
        SpeechDailyUsageService shared = SpeechTestSupport.inMemoryDailyUsage();
        new SpeechUsageLimiter(clock, SpeechTestSupport.properties(2, 100, 1000), shared).acquire(MEMBER, 60);

        SpeechUsageLimiter restarted = new SpeechUsageLimiter(clock, SpeechTestSupport.properties(2, 100, 1000), shared);

        assertThatThrownBy(() -> restarted.acquire(MEMBER, 41))
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getErrorCode())
                .isEqualTo(SpeechErrorCode.DAILY_LIMIT_EXCEEDED);
    }
}
