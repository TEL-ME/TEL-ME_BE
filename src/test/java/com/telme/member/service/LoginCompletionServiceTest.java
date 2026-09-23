package com.telme.member.service;

import static com.telme.chat.service.HttpSessionChatActorProvider.GUEST_ID_ATTRIBUTE;
import static com.telme.chat.service.HttpSessionChatActorProvider.USER_ID_ATTRIBUTE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.telme.member.entity.User;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;

class LoginCompletionServiceTest {

    private final SecurityContextRepository securityContextRepository = mock(SecurityContextRepository.class);
    private final LoginCompletionService service = new LoginCompletionService(securityContextRepository);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("guestId가 있으면 세션ID를 재발급하고 guestId 속성을 지운 뒤 userId를 저장한다")
    void guestId_있으면_세션ID_재발급하고_guestId_지운다() {
        MockHttpSession session = new MockHttpSession();
        UUID guestId = UUID.randomUUID();
        session.setAttribute(GUEST_ID_ATTRIBUTE, guestId);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        String sessionIdBefore = session.getId();
        User user = User.builder().userId(1L).build();

        service.completeLogin(user, guestId, request, new MockHttpServletResponse());

        assertThat(request.getSession(false).getId()).isNotEqualTo(sessionIdBefore);
        assertThat(request.getSession(false).getAttribute(GUEST_ID_ATTRIBUTE)).isNull();
        assertThat(request.getSession(false).getAttribute(USER_ID_ATTRIBUTE)).isEqualTo(1L);
        verify(securityContextRepository).saveContext(any(), any(), any());
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication.getPrincipal()).isEqualTo(1L);
        assertThat(authentication.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_USER");
    }

    @Test
    @DisplayName("guestId가 없으면 userId만 세션에 저장한다")
    void guestId_없으면_userId만_저장() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        User user = User.builder().userId(2L).role(User.Role.ADMIN).build();

        service.completeLogin(user, null, request, new MockHttpServletResponse());

        assertThat(request.getSession(false).getAttribute(USER_ID_ATTRIBUTE)).isEqualTo(2L);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_ADMIN");
    }
}
