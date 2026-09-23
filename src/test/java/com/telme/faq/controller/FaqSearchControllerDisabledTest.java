package com.telme.faq.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.telme.faq.service.FaqSearchService;
import com.telme.global.config.SecurityConfig;
import com.telme.member.service.GuestIdentityService;
import com.telme.member.service.KakaoLoginFailureHandler;
import com.telme.member.service.KakaoLoginSuccessHandler;
import com.telme.member.service.KakaoOAuth2UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// enabled=false면 컨트롤러 빈이 안 생기는지 확인 (HTTP 대신 빈 등록 여부로 직접 검증)
@WebMvcTest(FaqSearchController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "faq.search-test-api-enabled=false")
class FaqSearchControllerDisabledTest {

    @Autowired
    private ApplicationContext context;

    @MockitoBean
    private FaqSearchService faqSearchService;

    @MockitoBean
    private GuestIdentityService guestIdentityService;

    // SecurityConfig가 securityFilterChain 빈에서 요구하는 OAuth2 로그인 의존성 — 웹 슬라이스에는 없어 목으로 채운다
    @MockitoBean
    private KakaoOAuth2UserService kakaoOAuth2UserService;

    @MockitoBean
    private KakaoLoginSuccessHandler kakaoLoginSuccessHandler;

    @MockitoBean
    private KakaoLoginFailureHandler kakaoLoginFailureHandler;

    @Test
    @DisplayName("faq.search-test-api-enabled=false면 FaqSearchController 빈이 등록되지 않는다")
    void 비활성화면_컨트롤러_빈이_없다() {
        assertThat(context.getBeanNamesForType(FaqSearchController.class)).isEmpty();
    }
}
