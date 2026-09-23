package com.telme.member.service;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

// 카카오 원본 클레임 추출만 담당 — 회원 조회·생성·연결은 세션에 접근 가능한 성공 핸들러에서 처리한다
@Service
@RequiredArgsConstructor
public class KakaoOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final DefaultOAuth2UserService delegate;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        Map<String, Object> attributes = delegate.loadUser(userRequest).getAttributes();
        String providerUserId = extractProviderUserId(attributes);
        String email = extractEmail(attributes);
        return new KakaoOAuth2User(providerUserId, email, attributes);
    }

    private String extractProviderUserId(Map<String, Object> attributes) {
        Object id = attributes.get("id");
        if (id == null || String.valueOf(id).isBlank()) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("invalid_user_info_response", "카카오 사용자 식별자(id)가 없습니다.", null));
        }
        return String.valueOf(id);
    }

    // 이메일 동의 없음·미인증·재사용(is_email_valid=false) 상태는 기존 계정 매칭에 못 쓴다 — 그냥 일반 신규 가입으로 진행
    @SuppressWarnings("unchecked")
    private String extractEmail(Map<String, Object> attributes) {
        if (!(attributes.get("kakao_account") instanceof Map<?, ?> account)) {
            return null;
        }
        if (!Boolean.TRUE.equals(account.get("is_email_verified")) || !Boolean.TRUE.equals(account.get("is_email_valid"))) {
            return null;
        }
        Object email = account.get("email");
        return email == null ? null : String.valueOf(email);
    }
}
