package com.telme.member.service;

import com.telme.member.entity.SocialAccount;
import org.springframework.security.oauth2.core.user.OAuth2User;

// 공급자별 OAuth 클레임을 내부 회원 처리에 필요한 공통 값으로 노출한다.
public interface SocialOAuth2Principal extends OAuth2User {

    SocialAccount.Provider getProvider();

    String getProviderUserId();

    String getEmail();

    String getDisplayName();
}
