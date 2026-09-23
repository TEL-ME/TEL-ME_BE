package com.telme.member.service;

import com.telme.chat.service.HttpSessionChatActorProvider;
import com.telme.global.common.exception.GeneralException;
import com.telme.member.entity.User;
import com.telme.member.exception.MemberErrorCode;
import com.telme.member.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CurrentMemberResolver {

    private final UserRepository userRepository;
    private final MemberStatusChecker memberStatusChecker;

    // SecurityConfig가 .authenticated()로 미인증 요청은 이미 막지만, 실제 신원은 USER_ID_ATTRIBUTE로만 읽는다(로그인 방식마다
    // principal 타입이 달라서) — 두 상태가 어긋나면(인증은 됐는데 세션에 userId가 없음) 방어적으로 401 처리한다
    public User resolve(HttpServletRequest request) {
        if (!isAuthenticated()) {
            throw new GeneralException(MemberErrorCode.UNAUTHENTICATED);
        }
        Long userId = readUserId(request);
        if (userId == null) {
            throw new GeneralException(MemberErrorCode.UNAUTHENTICATED);
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(MemberErrorCode.UNAUTHENTICATED));
        // 세션이 살아있는 동안 상태가 정지·탈퇴로 바뀔 수 있다 — "내 계정" 류 API는 매번 다시 확인한다
        memberStatusChecker.checkActive(user);
        return user;
    }

    private boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    private Long readUserId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object value = session.getAttribute(HttpSessionChatActorProvider.USER_ID_ATTRIBUTE);
        return value instanceof Long userId ? userId : null;
    }
}
