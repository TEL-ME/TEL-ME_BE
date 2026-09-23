package com.telme.member.service;

import com.telme.chat.service.HttpSessionChatActorProvider;
import com.telme.member.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LoginCompletionService {

    private final SecurityContextRepository securityContextRepository;

    // DB 커밋 후에만 호출 — 세션ID 재발급 -> SecurityContext 저장 -> userId 설정. 이메일/카카오 로그인·연결 공통
    public void completeLogin(User user, UUID guestId, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        HttpSession session = httpRequest.getSession();
        if (guestId != null) {
            // 승계 처리는 1회로 끝나야 한다 — 지우지 않으면 같은 세션에서 재로그인 시 이미 승계된 게스트를 다시 읽어 재승계를 시도한다
            session.removeAttribute(HttpSessionChatActorProvider.GUEST_ID_ATTRIBUTE);
        }

        // 세션 고정 공격 방지 — invalidate 후 재생성 대신 속성을 유지한 채 ID만 바꾼다
        httpRequest.changeSessionId();

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                user.getUserId(), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        // SecurityContextHolderFilter는 로드만 하고 저장은 안 하므로 명시적으로 저장해야 다음 요청에서도 인증이 유지된다
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        session.setAttribute(HttpSessionChatActorProvider.USER_ID_ATTRIBUTE, user.getUserId());
    }
}
