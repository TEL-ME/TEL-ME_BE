package com.telme.member.service;

import java.util.List;
import java.util.Map;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

// 카카오 원본 클레임만 담는다 — 회원 조회·생성·연결(로그인 대 계정연결 판단 포함)은 전부
// 성공 핸들러에서 처리한다(로드 시점엔 세션에 접근할 수 없어 그 판단을 할 수 없다)
@Getter
public class KakaoOAuth2User implements OAuth2User {

    private final String providerUserId;
    private final String email;
    private final Map<String, Object> attributes;

    public KakaoOAuth2User(String providerUserId, String email, Map<String, Object> attributes) {
        this.providerUserId = providerUserId;
        this.email = email;
        this.attributes = attributes;
    }

    @Override
    public List<GrantedAuthority> getAuthorities() {
        // 성공 핸들러가 실제 회원을 해석한 뒤 SecurityContext를 다시 세팅하므로 여기서는 의미 없음
        return List.of();
    }

    @Override
    public String getName() {
        return providerUserId;
    }
}
