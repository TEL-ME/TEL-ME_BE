package com.telme.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.telme.member.entity.SocialAccount;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

class GoogleOidcUserServiceTest {

    private final OidcUserService delegate = mock(OidcUserService.class);
    private final GoogleOidcUserService service = new GoogleOidcUserService(delegate);
    private final OidcUserRequest request = mock(OidcUserRequest.class);

    @Test
    @DisplayName("Google sub·검증된 email·name을 공통 principal로 추출한다")
    void 정상_클레임을_추출한다() {
        OidcUser rawUser = rawUser("google-123", "User@Example.COM", true, "  구글 회원  ");
        when(delegate.loadUser(request)).thenReturn(rawUser);

        OidcUser result = service.loadUser(request);

        assertThat(result).isInstanceOf(GoogleOidcUser.class);
        GoogleOidcUser googleUser = (GoogleOidcUser) result;
        assertThat(googleUser.getProvider()).isEqualTo(SocialAccount.Provider.GOOGLE);
        assertThat(googleUser.getProviderUserId()).isEqualTo("google-123");
        assertThat(googleUser.getEmail()).isEqualTo("user@example.com");
        assertThat(googleUser.getDisplayName()).isEqualTo("구글 회원");
        assertThat(googleUser.getDelegate()).isSameAs(rawUser);
    }

    @Test
    @DisplayName("검증되지 않은 이메일은 기존 계정 발견에 사용하지 않는다")
    void 미검증_이메일은_null이다() {
        givenRawUser("google-124", "unverified@example.com", false, "회원");

        GoogleOidcUser result = (GoogleOidcUser) service.loadUser(request);

        assertThat(result.getEmail()).isNull();
    }

    @Test
    @DisplayName("email_verified가 없으면 이메일을 사용하지 않는다")
    void 이메일_검증_여부가_없으면_null이다() {
        givenRawUser("google-125", "unknown@example.com", null, "회원");

        GoogleOidcUser result = (GoogleOidcUser) service.loadUser(request);

        assertThat(result.getEmail()).isNull();
    }

    @Test
    @DisplayName("검증된 이메일이 비어 있으면 email 없이 추출한다")
    void 이메일이_비어_있으면_null이다() {
        givenRawUser("google-126", "   ", true, "회원");

        GoogleOidcUser result = (GoogleOidcUser) service.loadUser(request);

        assertThat(result.getEmail()).isNull();
    }

    @Test
    @DisplayName("name이 공백뿐이면 표시 이름 없이 추출한다")
    void 표시_이름이_공백이면_null이다() {
        givenRawUser("google-127", null, false, "   ");

        GoogleOidcUser result = (GoogleOidcUser) service.loadUser(request);

        assertThat(result.getDisplayName()).isNull();
    }

    @Test
    @DisplayName("name은 users.name 길이를 넘지 않게 자르고 서로게이트 쌍을 분리하지 않는다")
    void 표시_이름_길이를_제한한다() {
        String longName = "가".repeat(49) + "😀" + "뒤쪽";
        givenRawUser("google-128", null, false, longName);

        GoogleOidcUser result = (GoogleOidcUser) service.loadUser(request);

        assertThat(result.getDisplayName()).isEqualTo("가".repeat(49));
        assertThat(result.getDisplayName().length()).isLessThanOrEqualTo(50);
    }

    @Test
    @DisplayName("sub가 없으면 OAuth 인증 실패로 처리한다")
    void sub가_없으면_예외() {
        givenRawUser(" ", "user@example.com", true, "회원");

        assertThatThrownBy(() -> service.loadUser(request))
                .isInstanceOf(OAuth2AuthenticationException.class);
    }

    private void givenRawUser(String subject, String email, Boolean emailVerified, String fullName) {
        OidcUser user = rawUser(subject, email, emailVerified, fullName);
        when(delegate.loadUser(request)).thenReturn(user);
    }

    private OidcUser rawUser(String subject, String email, Boolean emailVerified, String fullName) {
        OidcUser user = mock(OidcUser.class);
        when(user.getSubject()).thenReturn(subject);
        when(user.getEmail()).thenReturn(email);
        when(user.getEmailVerified()).thenReturn(emailVerified);
        when(user.getFullName()).thenReturn(fullName);
        return user;
    }
}
