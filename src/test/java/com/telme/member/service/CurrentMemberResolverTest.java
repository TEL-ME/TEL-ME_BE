package com.telme.member.service;

import static com.telme.chat.service.HttpSessionChatActorProvider.USER_ID_ATTRIBUTE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.telme.global.common.exception.GeneralException;
import com.telme.member.entity.User;
import com.telme.member.exception.MemberErrorCode;
import com.telme.member.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

class CurrentMemberResolverTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final MemberStatusChecker memberStatusChecker = new MemberStatusChecker();
    private final CurrentMemberResolver resolver = new CurrentMemberResolver(userRepository, memberStatusChecker);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("인증되고 세션에 userId가 있으면 해당 회원을 반환한다")
    void 정상_조회() {
        authenticateAs(1L);
        MockHttpServletRequest request = requestWithUserId(1L);
        User user = User.builder().userId(1L).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThat(resolver.resolve(request)).isEqualTo(user);
    }

    @Test
    @DisplayName("SecurityContext에 인증 정보가 없으면 401")
    void 인증_없으면_401() {
        MockHttpServletRequest request = requestWithUserId(1L);

        assertThatThrownBy(() -> resolver.resolve(request))
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getErrorCode())
                .isEqualTo(MemberErrorCode.UNAUTHENTICATED);
    }

    @Test
    @DisplayName("익명 Authentication이면 401")
    void 익명_인증이면_401() {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymous", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        SecurityContextHolder.setContext(context);
        MockHttpServletRequest request = requestWithUserId(1L);

        assertThatThrownBy(() -> resolver.resolve(request))
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getErrorCode())
                .isEqualTo(MemberErrorCode.UNAUTHENTICATED);
    }

    @Test
    @DisplayName("SecurityContext는 인증됐는데 세션에 userId가 없으면 401")
    void 세션에_userId_없으면_401() {
        authenticateAs(1L);
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThatThrownBy(() -> resolver.resolve(request))
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getErrorCode())
                .isEqualTo(MemberErrorCode.UNAUTHENTICATED);
    }

    @Test
    @DisplayName("세션의 userId에 해당하는 회원이 DB에 없으면 401")
    void 회원이_없으면_401() {
        authenticateAs(1L);
        MockHttpServletRequest request = requestWithUserId(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolve(request))
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getErrorCode())
                .isEqualTo(MemberErrorCode.UNAUTHENTICATED);
    }

    @Test
    @DisplayName("정지된 회원이면 상태 오류를 던진다")
    void 정지된_회원은_거부() {
        authenticateAs(1L);
        MockHttpServletRequest request = requestWithUserId(1L);
        User user = User.builder().userId(1L).status(User.Status.SUSPENDED).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> resolver.resolve(request))
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getErrorCode())
                .isEqualTo(MemberErrorCode.ACCOUNT_SUSPENDED);
    }

    private void authenticateAs(Long userId) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(userId, null, List.of()));
        SecurityContextHolder.setContext(context);
    }

    private MockHttpServletRequest requestWithUserId(Long userId) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(USER_ID_ATTRIBUTE, userId);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        return request;
    }
}
