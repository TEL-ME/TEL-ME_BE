package com.telme.faq.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.telme.faq.dto.res.FaqListResponse;
import com.telme.faq.dto.res.FaqResponse;
import com.telme.faq.exception.FaqErrorCode;
import com.telme.faq.service.FaqQueryService;
import com.telme.global.common.exception.GeneralException;
import com.telme.global.config.SecurityConfig;
import com.telme.member.repository.UserRepository;
import com.telme.member.service.GuestIdentityService;
import com.telme.member.service.KakaoAuthorizationFailureHandler;
import com.telme.member.service.KakaoLinkRequestStore;
import com.telme.member.service.KakaoLoginFailureHandler;
import com.telme.member.service.KakaoLoginSuccessHandler;
import com.telme.member.service.KakaoOAuth2UserService;
import com.telme.member.service.MemberStatusChecker;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// SecurityConfig를 Import해야 로그인 없이 열리는지까지 확인된다
@WebMvcTest(FaqController.class)
@Import({SecurityConfig.class, MemberStatusChecker.class})
class FaqControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // MemberStatusFilter가 요청마다 회원을 읽어 SecurityConfig가 이 빈을 요구한다.
    // 슬라이스 테스트의 principal은 문자열이라 필터는 그대로 통과시킨다
    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private FaqQueryService faqQueryService;

    @MockitoBean
    private GuestIdentityService guestIdentityService;

    // SecurityConfig가 securityFilterChain 빈에서 요구하는 OAuth2 로그인 의존성 — 웹 슬라이스에는 없어 목으로 채운다
    @MockitoBean
    private KakaoOAuth2UserService kakaoOAuth2UserService;

    @MockitoBean
    private KakaoLoginSuccessHandler kakaoLoginSuccessHandler;

    @MockitoBean
    private KakaoLoginFailureHandler kakaoLoginFailureHandler;

    @MockitoBean
    private KakaoAuthorizationFailureHandler kakaoAuthorizationFailureHandler;

    @MockitoBean
    private KakaoLinkRequestStore kakaoLinkRequestStore;

    @Test
    @DisplayName("로그인하지 않아도 목록과 상세를 조회한다")
    void 비로그인도_조회한다() throws Exception {
        when(faqQueryService.getFaqs(any())).thenReturn(new FaqListResponse(List.of(), 0, 20, 0, 0));
        when(faqQueryService.getFaq(1L)).thenReturn(new FaqResponse(1L, "USIM", "질문", "답변"));

        mockMvc.perform(get("/api/v1/faqs")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/faqs/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.answer").value("답변"));
    }

    @Test
    @DisplayName("없는 카테고리·정렬 코드와 범위를 벗어난 값은 400이다")
    void 잘못된_조건은_400이다() throws Exception {
        mockMvc.perform(get("/api/v1/faqs").param("category", "NOPE")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/faqs").param("sort", "NOPE")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/faqs").param("size", "101")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/faqs/0")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("공개 중이 아닌 FAQ는 404다")
    void 공개_중이_아니면_404다() throws Exception {
        when(faqQueryService.getFaq(9L)).thenThrow(new GeneralException(FaqErrorCode.FAQ_NOT_FOUND));

        mockMvc.perform(get("/api/v1/faqs/9"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FAQ404-0"));
    }
}