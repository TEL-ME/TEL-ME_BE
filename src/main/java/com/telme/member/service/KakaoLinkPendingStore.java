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
public class KakaoLinkPendingStore {

    public static final String SESSION_ATTRIBUTE = "kakaoLinkPending";
    private static final Duration TTL = Duration.ofMinutes(3);

    private final Clock clock;

    // 세션당 진행 중인 카카오 OAuth 흐름은 하나만 지원 — 새로 시작하면 이전 것을 덮어쓴다
    public void issue(HttpServletRequest request, Long targetUserId) {
        request.getSession().setAttribute(SESSION_ATTRIBUTE, new KakaoLinkPending(targetUserId, clock.instant()));
    }

    // 읽는 즉시 제거(단일 사용). 반환값 null은 "연결 시도 자체가 없었음"(일반 로그인)을 뜻하고,
    // 있었지만 만료된 경우는 null이 아니라 예외로 구분한다 — 둘을 같이 취급하면 연결 시도가 있었는데
    // 늦게 돌아온 콜백이 조용히 일반 로그인/가입으로 처리돼버린다
    public KakaoLinkPending consume(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        KakaoLinkPending pending = (KakaoLinkPending) session.getAttribute(SESSION_ATTRIBUTE);
        session.removeAttribute(SESSION_ATTRIBUTE);
        if (pending == null) {
            return null;
        }
        if (pending.isExpired(clock.instant(), TTL)) {
            throw new GeneralException(MemberErrorCode.KAKAO_LINK_SESSION_EXPIRED);
        }
        return pending;
    }

    public void clear(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.removeAttribute(SESSION_ATTRIBUTE);
        }
    }
}
