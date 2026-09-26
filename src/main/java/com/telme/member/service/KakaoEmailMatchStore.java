package com.telme.member.service;

import com.telme.global.common.exception.GeneralException;
import com.telme.member.exception.MemberErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KakaoEmailMatchStore {

    public static final String SESSION_ATTRIBUTE = "kakaoEmailMatch";
    private static final Duration TTL = Duration.ofMinutes(10);
    private static final int MAX_ATTEMPTS = 5;

    private final Clock clock;

    // 같은 세션에서 새로 카카오 인증을 시작하면 이전 대기 상태를 그대로 덮어써 최신 것만 유효하게 한다
    public void issue(HttpServletRequest request, String providerUserId, Long matchedUserId, String matchedEmail) {
        request.getSession().setAttribute(
                SESSION_ATTRIBUTE, KakaoEmailMatch.issue(providerUserId, matchedUserId, matchedEmail, clock.instant()));
    }

    // 만료·시도횟수 초과면 바로 제거하고 예외 — 오래전에 인증한 카카오 계정이 엉뚱한 시점에 연결되는 것을 막는다
    public KakaoEmailMatch require(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        KakaoEmailMatch pending = session == null ? null : (KakaoEmailMatch) session.getAttribute(SESSION_ATTRIBUTE);
        if (pending == null || pending.isExpired(clock.instant(), TTL) || pending.failedAttempts() >= MAX_ATTEMPTS) {
            clear(request);
            throw new GeneralException(MemberErrorCode.KAKAO_LINK_SESSION_EXPIRED);
        }
        return pending;
    }

    public void registerFailedAttempt(HttpServletRequest request, KakaoEmailMatch pending) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return;
        }
        KakaoEmailMatch updated = pending.withFailedAttempt();
        if (updated.failedAttempts() >= MAX_ATTEMPTS) {
            session.removeAttribute(SESSION_ATTRIBUTE);
        } else {
            session.setAttribute(SESSION_ATTRIBUTE, updated);
        }
    }

    public void clear(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.removeAttribute(SESSION_ATTRIBUTE);
        }
    }
}
