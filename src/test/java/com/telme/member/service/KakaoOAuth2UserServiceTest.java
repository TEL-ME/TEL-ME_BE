package com.telme.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    private final DefaultOAuth2UserService delegate = mock(DefaultOAuth2UserService.class);
    private final KakaoOAuth2UserService service = new KakaoOAuth2UserService(delegate);
    private final OAuth2UserRequest request = mock(OAuth2UserRequest.class);

    @Test
    @DisplayName("카카오 id·인증된 kakao_account.email을 추출한다")
    void 정상_흐름이면_원본_클레임을_추출한다() {
        Map<String, Object> attributes = Map.of(
                "id", 12345L,
                "kakao_account", Map.of("email", "user@kakao.com", "is_email_verified", true, "is_email_valid", true));
        when(delegate.loadUser(request)).thenReturn(rawUser(attributes));

        OAuth2User result = service.loadUser(request);

        assertThat(result).isInstanceOf(KakaoOAuth2User.class);
        KakaoOAuth2User kakaoUser = (KakaoOAuth2User) result;
        assertThat(kakaoUser.getProviderUserId()).isEqualTo("12345");
        assertThat(kakaoUser.getEmail()).isEqualTo("user@kakao.com");
    }

    @Test
    @DisplayName("kakao_account가 없으면 email 없이 추출한다")
    void kakao_account가_없으면_email_null() {
        Map<String, Object> attributes = Map.of("id", 999L);
        when(delegate.loadUser(request)).thenReturn(rawUser(attributes));

        KakaoOAuth2User result = (KakaoOAuth2User) service.loadUser(request);

        assertThat(result.getProviderUserId()).isEqualTo("999");
        assertThat(result.getEmail()).isNull();
    }

    @Test
    @DisplayName("이메일이 미인증 상태면 email 없이 추출한다")
    void 이메일_미인증이면_email_null() {
        Map<String, Object> attributes = Map.of(
                "id", 998L,
                "kakao_account", Map.of("email", "unverified@kakao.com", "is_email_verified", false, "is_email_valid", true));
        when(delegate.loadUser(request)).thenReturn(rawUser(attributes));

        KakaoOAuth2User result = (KakaoOAuth2User) service.loadUser(request);

        assertThat(result.getEmail()).isNull();
    }

    @Test
    @DisplayName("이메일이 다른 계정에 재사용돼 만료(is_email_valid=false)면 email 없이 추출한다")
    void 이메일_재사용_만료면_email_null() {
        Map<String, Object> attributes = Map.of(
                "id", 997L,
                "kakao_account", Map.of("email", "reused@kakao.com", "is_email_verified", true, "is_email_valid", false));
        when(delegate.loadUser(request)).thenReturn(rawUser(attributes));

        KakaoOAuth2User result = (KakaoOAuth2User) service.loadUser(request);

        assertThat(result.getEmail()).isNull();
    }

    @Test
    @DisplayName("id가 빈 문자열이면 OAuth2AuthenticationException을 던진다")
    void id가_비어있으면_예외() {
        Map<String, Object> attributes = Map.of("id", "");
        when(delegate.loadUser(request)).thenReturn(rawUser(attributes));

        assertThatThrownBy(() -> service.loadUser(request)).isInstanceOf(OAuth2AuthenticationException.class);
    }

    private OAuth2User rawUser(Map<String, Object> attributes) {
        return new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_USER")), attributes, "id");
    }
}
