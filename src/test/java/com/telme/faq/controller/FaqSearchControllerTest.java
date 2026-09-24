package com.telme.faq.controller;

import com.telme.member.service.KakaoLinkRequestStore;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.telme.faq.dto.res.FaqSearchResponse;
import com.telme.faq.service.FaqSearchService;
import com.telme.global.config.SecurityConfig;
import com.telme.member.service.GuestIdentityService;
import com.telme.member.service.KakaoLoginFailureHandler;
import com.telme.member.service.KakaoLoginSuccessHandler;
import com.telme.member.service.KakaoOAuth2UserService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// SecurityConfig 미Import 시 @WebMvcTest가 기본 보안 설정으로 돌아 permitAll이 검증되지 않음
@WebMvcTest(FaqSearchController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "faq.search-test-api-enabled=true")
class FaqSearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private KakaoLinkRequestStore kakaoLinkRequestStore;

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
    @WithMockUser
    @DisplayName("정상 요청이면 200과 검색 결과를 반환한다")
    void 정상_요청이면_200을_반환한다() throws Exception {
        when(faqSearchService.search(any())).thenReturn(List.of(
                new FaqSearchResponse(1L, "USIM", "유심 재발급 얼마예요?", "7,700원입니다.",
                        0.9, 1, LocalDate.of(2026, 9, 21), 1)));

        mockMvc.perform(get("/api/v1/faq/search").param("query", "유심 재발급 얼마예요?"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result[0].faqId").value(1));
    }

    @Test
    @WithMockUser
    @DisplayName("topK가 0 이하면 500이 아니라 400과 FAQ400-1을 반환한다")
    void topK가_0이면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/faq/search").param("query", "유심").param("topK", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("FAQ400-1"));
    }

    @Test
    @WithMockUser
    @DisplayName("query가 없으면 400을 반환한다")
    void query가_없으면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/faq/search"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    @DisplayName("query가 500자를 넘으면 400을 반환한다")
    void query가_너무_길면_400을_반환한다() throws Exception {
        String tooLong = "가".repeat(501);

        mockMvc.perform(get("/api/v1/faq/search").param("query", tooLong))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    @DisplayName("topK가 10을 넘으면 400을 반환한다")
    void topK가_너무_크면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/faq/search").param("query", "유심").param("topK", "11"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("인증 없이도(permitAll) 호출할 수 있다")
    void 비인증_요청도_permitAll로_호출된다() throws Exception {
        when(faqSearchService.search(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/faq/search").param("query", "유심"))
                .andExpect(status().isOk());
    }
}
