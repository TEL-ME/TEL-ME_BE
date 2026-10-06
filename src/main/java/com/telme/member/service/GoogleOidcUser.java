package com.telme.member.service;

import com.telme.member.entity.SocialAccount;
import java.util.Collection;
import java.util.Map;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/** Google OIDC 원본 principal과 내부 회원 처리에 필요한 공통 값을 함께 보관한다. */
@Getter
public class GoogleOidcUser implements OidcUser, SocialOAuth2Principal {

    private final String providerUserId;
    private final String email;
    private final String displayName;
    private final OidcUser delegate;

    public GoogleOidcUser(String providerUserId, String email, String displayName, OidcUser delegate) {
        this.providerUserId = providerUserId;
        this.email = email;
        this.displayName = displayName;
        this.delegate = delegate;
    }

    @Override
    public SocialAccount.Provider getProvider() {
        return SocialAccount.Provider.GOOGLE;
    }

    @Override
    public Map<String, Object> getClaims() {
        return delegate.getClaims();
    }

    @Override
    public OidcUserInfo getUserInfo() {
        return delegate.getUserInfo();
    }

    @Override
    public OidcIdToken getIdToken() {
        return delegate.getIdToken();
    }

    @Override
    public Map<String, Object> getAttributes() {
        return delegate.getAttributes();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return delegate.getAuthorities();
    }

    @Override
    public String getName() {
        return providerUserId;
    }
}
