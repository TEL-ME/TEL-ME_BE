package com.telme.member.config;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;

@Configuration
@EnableConfigurationProperties({GuestProperties.class, Oauth2Properties.class})
public class MemberConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    // KakaoOAuth2UserService가 생성자로 주입받아 테스트에서 목으로 대체할 수 있게 빈으로 등록
    @Bean
    public DefaultOAuth2UserService defaultOAuth2UserService() {
        return new DefaultOAuth2UserService();
    }
}
