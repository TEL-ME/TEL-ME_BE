package com.telme.member.service;

import static com.telme.chat.service.HttpSessionChatActorProvider.USER_ID_ATTRIBUTE;
import static com.telme.member.entity.SocialAccount.Provider.KAKAO;
import static org.assertj.core.api.Assertions.assertThat;

import com.telme.member.dto.res.MemberMeResponse;
import com.telme.member.dto.res.MemberMeResponse.LoginMethod;
import com.telme.member.entity.SocialAccount;
import com.telme.member.entity.User;
import com.telme.member.repository.SocialAccountRepository;
import com.telme.member.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class MemberProfileServiceIntegrationTest {

    @Autowired
    private MemberProfileService memberProfileService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SocialAccountRepository socialAccountRepository;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("이메일 전용 회원은 이메일 로그인만 활성화되어 조회된다")
    void 이메일_전용_회원() {
        User user = userRepository.saveAndFlush(User.builder()
                .email(uniqueEmail())
                .passwordHash("HASHED")
                .name("이메일회원")
                .build());

        MemberMeResponse response = memberProfileService.getMe(authenticatedRequest(user.getUserId()));

        assertThat(response.authenticated()).isTrue();
        assertThat(response.name()).isEqualTo("이메일회원");
        assertThat(response.loginMethods()).containsExactly(LoginMethod.EMAIL);
    }

    @Test
    @DisplayName("카카오 전용 회원은 카카오 로그인만 연결되어 조회된다")
    void 카카오_전용_회원() {
        User user = userRepository.saveAndFlush(User.builder().name("카카오회원").build());
        socialAccountRepository.saveAndFlush(SocialAccount.builder()
                .user(user)
                .provider(KAKAO)
                .providerUserId("profile-kakao-" + UUID.randomUUID())
                .build());

        MemberMeResponse response = memberProfileService.getMe(authenticatedRequest(user.getUserId()));

        assertThat(response.email()).isNull();
        assertThat(response.loginMethods()).containsExactly(LoginMethod.KAKAO);
    }

    @Test
    @DisplayName("두 로그인 수단이 연결된 회원은 이메일과 카카오가 모두 활성화되어 조회된다")
    void 이메일과_카카오가_모두_연결된_회원() {
        User user = userRepository.saveAndFlush(User.builder()
                .email(uniqueEmail())
                .passwordHash("HASHED")
                .name("연결회원")
                .build());
        socialAccountRepository.saveAndFlush(SocialAccount.builder()
                .user(user)
                .provider(KAKAO)
                .providerUserId("profile-both-" + UUID.randomUUID())
                .build());

        MemberMeResponse response = memberProfileService.getMe(authenticatedRequest(user.getUserId()));

        assertThat(response.loginMethods()).containsExactly(LoginMethod.EMAIL, LoginMethod.KAKAO);
    }

    private MockHttpServletRequest authenticatedRequest(Long userId) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(userId, null, List.of()));
        SecurityContextHolder.setContext(context);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute(USER_ID_ATTRIBUTE, userId);
        return request;
    }

    private String uniqueEmail() {
        return "profile-" + UUID.randomUUID() + "@example.com";
    }
}
