package com.telme.member.service;

import com.telme.chat.service.HttpSessionChatActorProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class GuestIdResolver {

    // getSession(false) — 세션이 없는 요청(로그인 실패, 중복 가입, 게스트 활동이 아직 없는 신규 방문 등)마다
    // 빈 세션을 새로 만들지 않도록 조회만 한다. 이메일 로그인·카카오 로그인 양쪽에서 공용으로 쓴다.
    public UUID resolve(HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        if (session == null) {
            return null;
        }
        Object value = session.getAttribute(HttpSessionChatActorProvider.GUEST_ID_ATTRIBUTE);
        return value instanceof UUID uuid ? uuid : null;
    }
}
