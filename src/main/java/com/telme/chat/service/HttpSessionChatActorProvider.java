package com.telme.chat.service;

import com.telme.chat.exception.ChatErrorCode;
import com.telme.global.common.exception.GeneralException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class HttpSessionChatActorProvider implements ChatActorProvider {

    // TODO(auth): 인증 공통 식별자 제공 방식이 확정되면 이 세션 속성 기반 구현을 교체하거나 제거한다.
    public static final String USER_ID_ATTRIBUTE = "userId";
    public static final String GUEST_ID_ATTRIBUTE = "guestId";

    @Override
    public ChatActor getCurrentActor(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            throw new GeneralException(ChatErrorCode.UNAUTHENTICATED);
        }

        Long userId = toLong(session.getAttribute(USER_ID_ATTRIBUTE));
        UUID guestId = toUuid(session.getAttribute(GUEST_ID_ATTRIBUTE));
        if (userId == null && guestId == null) {
            throw new GeneralException(ChatErrorCode.UNAUTHENTICATED);
        }
        return new ChatActor(userId, guestId);
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.valueOf(text);
            } catch (NumberFormatException ignored) {
                throw new GeneralException(ChatErrorCode.UNAUTHENTICATED);
            }
        }
        return null;
    }

    private UUID toUuid(Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        if (value instanceof String text) {
            try {
                return UUID.fromString(text);
            } catch (IllegalArgumentException ignored) {
                throw new GeneralException(ChatErrorCode.UNAUTHENTICATED);
            }
        }
        return null;
    }
}
