package com.telme.chat.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.telme.chat.exception.ChatErrorCode;
import com.telme.chat.service.ChatActor;
import com.telme.global.common.exception.GeneralException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

class HttpSessionChatActorProviderTest {

    private final HttpSessionChatActorProvider provider = new HttpSessionChatActorProvider();

    @Test
    void resolvesMemberAndGuestIdentifiers() {
        UUID guestId = UUID.randomUUID();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionChatActorProvider.USER_ID_ATTRIBUTE, 3L);
        session.setAttribute(HttpSessionChatActorProvider.GUEST_ID_ATTRIBUTE, guestId);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);

        ChatActor actor = provider.getCurrentActor(request);

        assertThat(actor.userId()).isEqualTo(3L);
        assertThat(actor.guestId()).isEqualTo(guestId);
        assertThat(actor.isMember()).isTrue();
    }

    @Test
    void rejectsRequestWithoutChatIdentity() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThatThrownBy(() -> provider.getCurrentActor(request))
                .isInstanceOf(GeneralException.class)
                .extracting(exception -> ((GeneralException) exception).getErrorCode())
                .isEqualTo(ChatErrorCode.UNAUTHENTICATED);
    }
}
