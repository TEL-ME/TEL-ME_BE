package com.telme.member.service;

import static com.telme.chat.service.HttpSessionChatActorProvider.GUEST_ID_ATTRIBUTE;
import static com.telme.chat.service.HttpSessionChatActorProvider.USER_ID_ATTRIBUTE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telme.member.config.Oauth2Properties;
import com.telme.member.entity.User;
import com.telme.member.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

class KakaoLoginSuccessHandlerTest {

    private final GuestSuccessionService guestSuccessionService = mock(GuestSuccessionService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final TransactionTemplate transactionTemplate = new TransactionTemplate() {
        @Override
        public <T> T execute(TransactionCallback<T> action) {
            return action.doInTransaction(null);
        }
    };
    private final Oauth2Properties oauth2Properties = new Oauth2Properties("http://localhost:3000");
    private final KakaoLoginSuccessHandler handler =
            new KakaoLoginSuccessHandler(guestSuccessionService, userRepository, transactionTemplate, oauth2Properties);

    @Test
    @DisplayName("guestId가 있고 승계에 성공하면 세션 반영 후 성공 URL로 리다이렉트한다")
    void 승계_성공시_세션_반영_후_리다이렉트() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        UUID guestId = UUID.randomUUID();
        request.getSession().setAttribute(GUEST_ID_ATTRIBUTE, guestId);
        MockHttpServletResponse response = new MockHttpServletResponse();
        User userRef = User.builder().userId(5L).build();
        when(userRepository.getReferenceById(5L)).thenReturn(userRef);

        handler.onAuthenticationSuccess(request, response, authenticationOf(5L));

        verify(guestSuccessionService).succeedGuest(guestId, userRef);
        assertThat(request.getSession().getAttribute(USER_ID_ATTRIBUTE)).isEqualTo(5L);
        assertThat(request.getSession().getAttribute(GUEST_ID_ATTRIBUTE)).isNull();
        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/oauth/callback?success=true");
    }

    @Test
    @DisplayName("guestId가 없으면 승계를 호출하지 않고 세션에 userId만 반영한다")
    void guestId_없으면_승계_생략() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authenticationOf(9L));

        verify(guestSuccessionService, never()).succeedGuest(any(), any());
        assertThat(request.getSession().getAttribute(USER_ID_ATTRIBUTE)).isEqualTo(9L);
        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/oauth/callback?success=true");
    }

    @Test
    @DisplayName("승계가 실패하면 인증·세션을 정리하고 실패 URL로 리다이렉트한다")
    void 승계_실패시_인증과_세션을_정리한다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        UUID guestId = UUID.randomUUID();
        request.getSession().setAttribute(GUEST_ID_ATTRIBUTE, guestId);
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(userRepository.getReferenceById(5L)).thenReturn(User.builder().userId(5L).build());
        doThrow(new RuntimeException("강제 실패")).when(guestSuccessionService).succeedGuest(any(), any());
        Authentication authentication = authenticationOf(5L);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        try {
            handler.onAuthenticationSuccess(request, response, authentication);

            assertThat(request.getSession(false)).isNull();
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            assertThat(response.getRedirectedUrl())
                    .isEqualTo("http://localhost:3000/oauth/callback?success=false&reason=GUEST_SUCCESSION_FAILED");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private Authentication authenticationOf(Long userId) {
        OAuth2User principal = new KakaoOAuth2User(userId, User.Role.USER, java.util.Map.of("id", "raw-id"));
        return new org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken(
                principal, principal.getAuthorities(), "kakao");
    }
}
