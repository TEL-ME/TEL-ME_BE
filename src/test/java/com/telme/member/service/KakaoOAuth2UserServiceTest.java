package com.telme.member.service;

import static com.telme.member.entity.SocialAccount.Provider.KAKAO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telme.global.common.exception.GeneralException;
import com.telme.member.entity.User;
import com.telme.member.exception.MemberErrorCode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

class KakaoOAuth2UserServiceTest {

    private final SocialMemberFinder socialMemberFinder = mock(SocialMemberFinder.class);
    private final DefaultOAuth2UserService delegate = mock(DefaultOAuth2UserService.class);
    private final KakaoOAuth2UserService service = new KakaoOAuth2UserService(socialMemberFinder, delegate);
    private final OAuth2UserRequest request = mock(OAuth2UserRequest.class);

    @Test
    @DisplayName("카카오 id·kakao_account.email을 추출해 회원을 조회하고 KakaoOAuth2User로 감싼다")
    void 정상_흐름이면_회원을_찾아_감싼다() {
        Map<String, Object> attributes = Map.of(
                "id", 12345L,
                "kakao_account", Map.of("email", "user@kakao.com"));
        when(delegate.loadUser(request)).thenReturn(rawUser(attributes));
        User user = User.builder().userId(7L).role(User.Role.ADMIN).build();
        when(socialMemberFinder.findOrCreate(KAKAO, "12345", "user@kakao.com")).thenReturn(user);

        OAuth2User result = service.loadUser(request);

        assertThat(result).isInstanceOf(KakaoOAuth2User.class);
        KakaoOAuth2User kakaoUser = (KakaoOAuth2User) result;
        assertThat(kakaoUser.getUserId()).isEqualTo(7L);
        assertThat(kakaoUser.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    @DisplayName("kakao_account가 없으면 email 없이 조회한다")
    void kakao_account가_없으면_email_null() {
        Map<String, Object> attributes = Map.of("id", 999L);
        when(delegate.loadUser(request)).thenReturn(rawUser(attributes));
        when(socialMemberFinder.findOrCreate(eq(KAKAO), eq("999"), any())).thenReturn(User.builder().userId(1L).build());

        service.loadUser(request);

        verify(socialMemberFinder).findOrCreate(KAKAO, "999", null);
    }

    @Test
    @DisplayName("id가 빈 문자열이면 OAuth2AuthenticationException을 던진다")
    void id가_비어있으면_예외() {
        Map<String, Object> attributes = Map.of("id", "");
        when(delegate.loadUser(request)).thenReturn(rawUser(attributes));

        assertThatThrownBy(() -> service.loadUser(request)).isInstanceOf(OAuth2AuthenticationException.class);
    }

    @Test
    @DisplayName("회원 상태 오류(GeneralException)는 OAuth2AuthenticationException으로 변환된다")
    void 상태_오류는_OAuth2예외로_변환() {
        Map<String, Object> attributes = Map.of("id", 1L);
        when(delegate.loadUser(request)).thenReturn(rawUser(attributes));
        when(socialMemberFinder.findOrCreate(KAKAO, "1", null))
                .thenThrow(new GeneralException(MemberErrorCode.ACCOUNT_SUSPENDED));

        assertThatThrownBy(() -> service.loadUser(request))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .extracting(e -> ((OAuth2AuthenticationException) e).getError().getErrorCode())
                .isEqualTo(MemberErrorCode.ACCOUNT_SUSPENDED.getCode());
    }

    private OAuth2User rawUser(Map<String, Object> attributes) {
        return new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_USER")), attributes, "id");
    }
}
