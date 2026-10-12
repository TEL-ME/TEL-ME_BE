package com.telme.member.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;

@Configuration
@EnableConfigurationProperties({GuestProperties.class, Oauth2Properties.class})
public class MemberConfig {

    // KakaoOAuth2UserService가 생성자로 주입받아 테스트에서 목으로 대체할 수 있게 빈으로 등록
    @Bean
    public DefaultOAuth2UserService defaultOAuth2UserService() {
        return new DefaultOAuth2UserService();
    }

    // GoogleOidcUserService가 원본 OIDC 사용자 조회를 위임하고 테스트에서는 목으로 대체할 수 있게 등록
    @Bean
    public OidcUserService oidcUserService() {
        return new OidcUserService();
    }
}
