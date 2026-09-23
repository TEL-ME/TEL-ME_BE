package com.telme.member.service;

import com.telme.global.common.exception.GeneralException;
import com.telme.member.entity.SocialAccount;
import com.telme.member.entity.User;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KakaoOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final SocialMemberFinder socialMemberFinder;
    private final DefaultOAuth2UserService delegate;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        Map<String, Object> attributes = delegate.loadUser(userRequest).getAttributes();
        String providerUserId = extractProviderUserId(attributes);
        String email = extractEmail(attributes);

        User user;
        try {
            user = socialMemberFinder.findOrCreate(SocialAccount.Provider.KAKAO, providerUserId, email);
        } catch (GeneralException exception) {
            // 정지·탈퇴 등 — Spring Security가 인증 실패로 처리해 실패 핸들러로 보내도록 변환
            throw new OAuth2AuthenticationException(
                    new OAuth2Error(exception.getErrorCode().getCode(), exception.getErrorCode().getMessage(), null),
                    exception);
        }

        return new KakaoOAuth2User(user.getUserId(), user.getRole(), attributes);
    }

    private String extractProviderUserId(Map<String, Object> attributes) {
        Object id = attributes.get("id");
        if (id == null || String.valueOf(id).isBlank()) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("invalid_user_info_response", "카카오 사용자 식별자(id)가 없습니다.", null));
        }
        return String.valueOf(id);
    }

    @SuppressWarnings("unchecked")
    private String extractEmail(Map<String, Object> attributes) {
        if (attributes.get("kakao_account") instanceof Map<?, ?> account) {
            Object email = account.get("email");
            return email == null ? null : String.valueOf(email);
        }
        return null;
    }
}
