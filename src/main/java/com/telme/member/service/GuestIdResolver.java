package com.telme.member.service;

import com.telme.chat.service.HttpSessionChatActorProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class GuestIdResolver {

    // getSession(false) — 실패한 로그인·중복 가입까지 세션이 없는 요청마다 빈 세션을 새로 만들지 않도록 조회만 한다
    public UUID resolve(HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        if (session == null) {
            return null;
        }
        Object value = session.getAttribute(HttpSessionChatActorProvider.GUEST_ID_ATTRIBUTE);
        return value instanceof UUID uuid ? uuid : null;
    }
}
