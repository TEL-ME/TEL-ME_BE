package com.telme.member.filter;

import com.telme.chat.service.HttpSessionChatActorProvider;
import com.telme.member.service.GuestIdentityService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.WebUtils;

// userId/guestId 둘 다 없을 때만 Guest 발급. 세션 뮤텍스로 동시 중복 발급 방지
@Component
@RequiredArgsConstructor
public class GuestIdentityFilter extends OncePerRequestFilter {

    private static final RequestMatcher TARGET_PATHS = new OrRequestMatcher(
            PathPatternRequestMatcher.withDefaults().matcher("/api/chat/**"),
            PathPatternRequestMatcher.withDefaults().matcher("/api/v1/chat/**")
    );

    private final GuestIdentityService guestIdentityService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !TARGET_PATHS.matches(request);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        HttpSession session = request.getSession();

        // TODO: 쿠키 없는 최초 동시 요청은 세션이 갈려 guest가 중복 발급될 수 있음(뮤텍스는 같은 세션 내 경합만 방지).
        // 완전한 해결은 클라이언트가 guestId를 선발급해 헤더로 전달하는 방식으로 전환 필요(프런트 연동 시 후속 작업).
        if (!hasIdentity(session)) {
            synchronized (WebUtils.getSessionMutex(session)) {
                if (!hasIdentity(session)) {
                    UUID guestId = guestIdentityService.issueGuest();
                    session.setAttribute(HttpSessionChatActorProvider.GUEST_ID_ATTRIBUTE, guestId);
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    // TODO: 세션의 guestId가 DB에서 지워졌거나 만료된 경우는 검증하지 않는다(매 요청 DB 조회 비용 때문에 1차는 보류).
    // 만료 검증·재발급은 후속 작업.
    private boolean hasIdentity(HttpSession session) {
        return session.getAttribute(HttpSessionChatActorProvider.USER_ID_ATTRIBUTE) != null
                || session.getAttribute(HttpSessionChatActorProvider.GUEST_ID_ATTRIBUTE) != null;
    }
}
