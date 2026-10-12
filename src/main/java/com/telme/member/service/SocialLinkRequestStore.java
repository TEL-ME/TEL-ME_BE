package com.telme.member.service;

import com.telme.global.common.exception.GeneralException;
import com.telme.member.entity.SocialAccount;
import com.telme.member.exception.MemberErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SocialLinkRequestStore {

    public static final String SESSION_ATTRIBUTE = "socialLinkRequest";
    private static final Duration TTL = Duration.ofMinutes(3);

    private final Clock clock;

    public String issue(HttpServletRequest request, Long targetUserId, SocialAccount.Provider provider) {
        String token = UUID.randomUUID().toString();
        request.getSession().setAttribute(SESSION_ATTRIBUTE,
                new SocialLinkRequest(targetUserId, provider, clock.instant(), token, null));
        return token;
    }

    // 카카오 연결용 토큰으로 구글 인가를 시작하는 식의 공급자 바꿔치기를 막기 위해 registrationId도 대조한다
    public void bind(HttpServletRequest request, String startToken, String state, String registrationId) {
        HttpSession session = request.getSession(false);
        SocialLinkRequest pending = session == null ? null
                : (SocialLinkRequest) session.getAttribute(SESSION_ATTRIBUTE);
        if (pending == null || pending.isExpired(clock.instant(), TTL)
                || pending.startToken() == null || !pending.startToken().equals(startToken)
                || !matchesProvider(pending, registrationId)
                || state == null) {
            throw new GeneralException(MemberErrorCode.SOCIAL_LINK_SESSION_EXPIRED);
        }
        session.setAttribute(SESSION_ATTRIBUTE,
                new SocialLinkRequest(pending.targetUserId(), pending.provider(), pending.issuedAt(), null, state));
    }

    private boolean matchesProvider(SocialLinkRequest pending, String registrationId) {
        return registrationId != null
                && pending.provider().name().toLowerCase(Locale.ROOT).equals(registrationId);
    }

    public SocialLinkRequest consume(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        SocialLinkRequest pending = (SocialLinkRequest) session.getAttribute(SESSION_ATTRIBUTE);
        session.removeAttribute(SESSION_ATTRIBUTE);
        if (pending == null) {
            return null;
        }
        if (pending.isExpired(clock.instant(), TTL)
                || pending.state() == null || !pending.state().equals(request.getParameter("state"))) {
            throw new GeneralException(MemberErrorCode.SOCIAL_LINK_SESSION_EXPIRED);
        }
        return pending;
    }

    public void clearIfStateMatches(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        String state = request.getParameter("state");
        if (session == null || state == null) {
            return;
        }
        SocialLinkRequest pending = (SocialLinkRequest) session.getAttribute(SESSION_ATTRIBUTE);
        if (pending != null && state.equals(pending.state())) {
            session.removeAttribute(SESSION_ATTRIBUTE);
        }
    }

    public void clear(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.removeAttribute(SESSION_ATTRIBUTE);
        }
    }
}
