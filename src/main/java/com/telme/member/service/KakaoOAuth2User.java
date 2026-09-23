package com.telme.member.service;

import com.telme.member.entity.User;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

@Getter
public class KakaoOAuth2User implements OAuth2User {

    private final Long userId;
    private final List<GrantedAuthority> authorities;
    private final Map<String, Object> attributes;

    public KakaoOAuth2User(Long userId, User.Role role, Map<String, Object> attributes) {
        this.userId = userId;
        this.authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
        this.attributes = attributes;
    }

    @Override
    public String getName() {
        return String.valueOf(userId);
    }
}
