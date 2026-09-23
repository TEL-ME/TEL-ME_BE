package com.telme.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.telme.global.common.exception.GeneralException;
import com.telme.member.exception.MemberErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class PendingKakaoLinkStoreTest {

    private final Instant now = Instant.parse("2026-09-23T00:00:00Z");
    private final MutableClock clock = new MutableClock(now);
    private final PendingKakaoLinkStore store = new PendingKakaoLinkStore(clock);

    @Test
    @DisplayName("발급 직후에는 그대로 조회된다")
    void 발급_직후_조회() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        store.issue(request, "kakao-1", 10L, "match@example.com");
        PendingKakaoLink pending = store.require(request);

        assertThat(pending.providerUserId()).isEqualTo("kakao-1");
        assertThat(pending.matchedUserId()).isEqualTo(10L);
        assertThat(pending.matchedEmail()).isEqualTo("match@example.com");
        assertThat(pending.failedAttempts()).isZero();
    }

    @Test
    @DisplayName("세션에 없으면 만료 예외를 던진다")
    void 없으면_예외() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThatThrownBy(() -> store.require(request))
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getErrorCode())
                .isEqualTo(MemberErrorCode.KAKAO_LINK_SESSION_EXPIRED);
    }

    @Test
    @DisplayName("10분이 지나면 만료되어 제거되고 예외를 던진다")
    void 만료되면_예외() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        store.issue(request, "kakao-1", 10L, "match@example.com");

        clock.advance(Duration.ofMinutes(11));

        assertThatThrownBy(() -> store.require(request))
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getErrorCode())
                .isEqualTo(MemberErrorCode.KAKAO_LINK_SESSION_EXPIRED);
        assertThat(request.getSession(false).getAttribute(PendingKakaoLinkStore.SESSION_ATTRIBUTE)).isNull();
    }

    @Test
    @DisplayName("실패 시도가 5회 누적되면 제거되고 이후 조회는 예외를 던진다")
    void 시도횟수_초과하면_예외() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        store.issue(request, "kakao-1", 10L, "match@example.com");

        for (int i = 0; i < 5; i++) {
            PendingKakaoLink pending = (PendingKakaoLink)
                    request.getSession(false).getAttribute(PendingKakaoLinkStore.SESSION_ATTRIBUTE);
            store.registerFailedAttempt(request, pending);
        }

        assertThatThrownBy(() -> store.require(request))
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getErrorCode())
                .isEqualTo(MemberErrorCode.KAKAO_LINK_SESSION_EXPIRED);
    }

    @Test
    @DisplayName("같은 세션에서 다시 발급하면 이전 대기 상태를 덮어쓴다")
    void 재발급하면_덮어쓴다() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        store.issue(request, "kakao-old", 10L, "old@example.com");

        store.issue(request, "kakao-new", 20L, "new@example.com");

        PendingKakaoLink pending = store.require(request);
        assertThat(pending.providerUserId()).isEqualTo("kakao-new");
        assertThat(pending.matchedUserId()).isEqualTo(20L);
    }

    @Test
    @DisplayName("연결 성공 후 clear 하면 더 이상 조회되지 않는다")
    void clear하면_사라진다() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        store.issue(request, "kakao-1", 10L, "match@example.com");

        store.clear(request);

        assertThatThrownBy(() -> store.require(request)).isInstanceOf(GeneralException.class);
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
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
