package com.telme.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.telme.member.entity.SocialAccount;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

class GoogleOidcUserTest {

    @Test
    @DisplayName("Google 공통 회원 정보와 OIDC 원본 principal을 함께 제공한다")
    void 공통_정보와_OIDC_정보를_제공한다() {
        OidcUser delegate = mock(OidcUser.class);
        OidcIdToken idToken = mock(OidcIdToken.class);
        OidcUserInfo userInfo = mock(OidcUserInfo.class);
        Map<String, Object> claims = Map.of("sub", "google-123", "email", "user@example.com");
        Map<String, Object> attributes = Map.of("sub", "google-123");
        GrantedAuthority authority = new SimpleGrantedAuthority("OIDC_USER");
        Collection<GrantedAuthority> authorities = List.of(authority);
        when(delegate.getClaims()).thenReturn(claims);
        when(delegate.getAttributes()).thenReturn(attributes);
        when(delegate.getIdToken()).thenReturn(idToken);
        when(delegate.getUserInfo()).thenReturn(userInfo);
        doReturn(authorities).when(delegate).getAuthorities();

        GoogleOidcUser user = new GoogleOidcUser(
                "google-123", "user@example.com", "구글 회원", delegate);

        assertThat(user.getProvider()).isEqualTo(SocialAccount.Provider.GOOGLE);
        assertThat(user.getProviderUserId()).isEqualTo("google-123");
        assertThat(user.getEmail()).isEqualTo("user@example.com");
        assertThat(user.getDisplayName()).isEqualTo("구글 회원");
        assertThat(user.getName()).isEqualTo("google-123");
        assertThat(user.getClaims()).isSameAs(claims);
        assertThat(user.getAttributes()).isSameAs(attributes);
        assertThat(user.getIdToken()).isSameAs(idToken);
        assertThat(user.getUserInfo()).isSameAs(userInfo);
        assertThat(user.getAuthorities()).isSameAs(authorities);
    }
}
