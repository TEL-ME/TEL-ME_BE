package com.telme.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.telme.global.common.exception.GeneralException;
import com.telme.member.exception.MemberErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class KakaoLinkPendingStoreTest {

    private final Instant now = Instant.parse("2026-09-24T00:00:00Z");
    private final MutableClock clock = new MutableClock(now);
    private final KakaoLinkPendingStore store = new KakaoLinkPendingStore(clock);

    @Test
    @DisplayName("발급 직후에는 그대로 조회되고, 읽으면 제거된다(단일 사용)")
    void 발급_직후_조회하면_제거된다() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        store.issue(request, 30L);
        KakaoLinkPending pending = store.consume(request);

        assertThat(pending.targetUserId()).isEqualTo(30L);
        assertThat(store.consume(request)).isNull();
    }

    @Test
    @DisplayName("세션에 없으면 null을 반환한다")
    void 없으면_null() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThat(store.consume(request)).isNull();
    }

    @Test
    @DisplayName("3분이 지나면 만료 예외를 던지고(연결 시도가 있었다는 신호) 세션에서도 제거된다 — null과 구분")
    void 만료되면_예외() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        store.issue(request, 30L);

        clock.advance(Duration.ofMinutes(4));

        assertThatThrownBy(() -> store.consume(request))
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getErrorCode())
                .isEqualTo(MemberErrorCode.KAKAO_LINK_SESSION_EXPIRED);
        assertThat(request.getSession(false).getAttribute(KakaoLinkPendingStore.SESSION_ATTRIBUTE)).isNull();
    }

    @Test
    @DisplayName("재발급하면 이전 pending을 덮어쓴다")
    void 재발급하면_덮어쓴다() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        store.issue(request, 10L);

        store.issue(request, 20L);

        assertThat(store.consume(request).targetUserId()).isEqualTo(20L);
    }

    @Test
    @DisplayName("clear 하면 더 이상 조회되지 않는다")
    void clear하면_사라진다() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        store.issue(request, 30L);

        store.clear(request);

        assertThat(store.consume(request)).isNull();
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
