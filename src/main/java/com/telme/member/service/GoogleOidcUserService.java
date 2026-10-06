package com.telme.member.service;

import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

// Google OIDC 클레임 추출만 담당 — 회원 조회·생성·연결은 세션에 접근 가능한 성공 핸들러에서 처리한다
@Service
@RequiredArgsConstructor
public class GoogleOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private static final int MAX_MEMBER_NAME_LENGTH = 50;

    private final OidcUserService delegate;

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = delegate.loadUser(userRequest);
        String providerUserId = extractProviderUserId(oidcUser);
        String email = extractEmail(oidcUser);
        String displayName = extractDisplayName(oidcUser);
        return new GoogleOidcUser(providerUserId, email, displayName, oidcUser);
    }

    private String extractProviderUserId(OidcUser oidcUser) {
        String subject = oidcUser.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("invalid_user_info_response", "Google 사용자 식별자(sub)가 없습니다.", null));
        }
        return subject;
    }

    // Google이 소유 확인을 끝낸 이메일만 기존 계정 발견에 사용한다.
    private String extractEmail(OidcUser oidcUser) {
        if (!Boolean.TRUE.equals(oidcUser.getEmailVerified())) {
            return null;
        }
        String email = oidcUser.getEmail();
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private String extractDisplayName(OidcUser oidcUser) {
        String name = oidcUser.getFullName();
        if (name == null) {
            return null;
        }
        String normalized = name.strip();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() <= MAX_MEMBER_NAME_LENGTH) {
            return normalized;
        }
        int endIndex = MAX_MEMBER_NAME_LENGTH;
        if (Character.isHighSurrogate(normalized.charAt(endIndex - 1))) {
            endIndex--;
        }
        return normalized.substring(0, endIndex);
    }
}
