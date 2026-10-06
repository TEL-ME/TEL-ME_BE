package com.telme.member.service;

import static com.telme.member.entity.SocialAccount.Provider.KAKAO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telme.member.dto.res.MemberMeResponse;
import com.telme.member.dto.res.MemberMeResponse.LoginMethod;
import com.telme.member.entity.User;
import com.telme.member.repository.SocialAccountRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MemberProfileServiceTest {

    private final CurrentMemberResolver currentMemberResolver = mock(CurrentMemberResolver.class);
    private final SocialAccountRepository socialAccountRepository = mock(SocialAccountRepository.class);
    private final MemberProfileService service = new MemberProfileService(currentMemberResolver, socialAccountRepository);
    private final HttpServletRequest request = mock(HttpServletRequest.class);

    @Test
    @DisplayName("로그인하지 않았으면 GUEST 응답을 반환한다")
    void 미인증_회원_조회() {
        when(currentMemberResolver.resolveOptional(request)).thenReturn(Optional.empty());

        MemberMeResponse response = service.getMe(request);

        assertThat(response.authenticated()).isFalse();
        assertThat(response.role()).isEqualTo(MemberMeResponse.Role.GUEST);
        assertThat(response.userId()).isNull();
        assertThat(response.loginMethods()).isEmpty();
    }

    @Test
    @DisplayName("이메일과 비밀번호가 있고 카카오가 연결된 관리자의 정보를 반환한다")
    void 이메일과_카카오_로그인_수단_조회() {
        User user = User.builder()
                .userId(1L)
                .email("admin@example.com")
                .passwordHash("HASHED")
                .name("관리자")
                .role(User.Role.ADMIN)
                .build();
        when(currentMemberResolver.resolveOptional(request)).thenReturn(Optional.of(user));
        when(socialAccountRepository.existsByUser_UserIdAndProvider(1L, KAKAO)).thenReturn(true);

        MemberMeResponse response = service.getMe(request);

        assertThat(response.authenticated()).isTrue();
        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.email()).isEqualTo("admin@example.com");
        assertThat(response.name()).isEqualTo("관리자");
        assertThat(response.role()).isEqualTo(MemberMeResponse.Role.ADMIN);
        assertThat(response.loginMethods()).containsExactly(LoginMethod.EMAIL, LoginMethod.KAKAO);
        verify(socialAccountRepository).existsByUser_UserIdAndProvider(1L, KAKAO);
    }

    @Test
    @DisplayName("이메일 비밀번호가 없는 소셜 전용 회원은 이메일 로그인이 비활성화된다")
    void 소셜_전용_회원_조회() {
        User user = User.builder().userId(2L).name("카카오회원").build();
        when(currentMemberResolver.resolveOptional(request)).thenReturn(Optional.of(user));
        when(socialAccountRepository.existsByUser_UserIdAndProvider(2L, KAKAO)).thenReturn(true);

        MemberMeResponse response = service.getMe(request);

        assertThat(response.email()).isNull();
        assertThat(response.loginMethods()).containsExactly(LoginMethod.KAKAO);
    }
}
