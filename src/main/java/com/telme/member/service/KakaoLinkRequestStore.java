package com.telme.member.service;

import com.telme.global.common.exception.GeneralException;
import com.telme.member.exception.MemberErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KakaoLinkRequestStore {

    public static final String SESSION_ATTRIBUTE = "kakaoLinkRequest";
    private static final Duration TTL = Duration.ofMinutes(3);

    private final Clock clock;

    public String issue(HttpServletRequest request, Long targetUserId) {
        String token = UUID.randomUUID().toString();
        request.getSession().setAttribute(SESSION_ATTRIBUTE,
                new KakaoLinkRequest(targetUserId, clock.instant(), token, null));
        return token;
    }

    public void bind(HttpServletRequest request, String startToken, String state) {
        HttpSession session = request.getSession(false);
        KakaoLinkRequest pending = session == null ? null
                : (KakaoLinkRequest) session.getAttribute(SESSION_ATTRIBUTE);
        if (pending == null || pending.isExpired(clock.instant(), TTL)
                || pending.startToken() == null || !pending.startToken().equals(startToken)
                || state == null) {
            throw new GeneralException(MemberErrorCode.KAKAO_LINK_SESSION_EXPIRED);
        }
        session.setAttribute(SESSION_ATTRIBUTE,
                new KakaoLinkRequest(pending.targetUserId(), pending.issuedAt(), null, state));
    }

    public KakaoLinkRequest consume(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        KakaoLinkRequest pending = (KakaoLinkRequest) session.getAttribute(SESSION_ATTRIBUTE);
        session.removeAttribute(SESSION_ATTRIBUTE);
        if (pending == null) {
            return null;
        }
        if (pending.isExpired(clock.instant(), TTL)
                || pending.state() == null || !pending.state().equals(request.getParameter("state"))) {
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
