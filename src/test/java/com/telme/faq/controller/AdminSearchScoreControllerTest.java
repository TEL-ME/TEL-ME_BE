package com.telme.faq.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.telme.faq.dto.res.AdminSearchScoreResponse;
import com.telme.faq.service.AdminSearchScoreQueryService;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// SecurityConfig를 Import하지 않으면 @WebMvcTest가 기본 보안 설정으로 돌아 ADMIN 제한이 검증되지 않는다
@WebMvcTest(AdminSearchScoreController.class)
@Import({SecurityConfig.class, MemberStatusChecker.class})
class AdminSearchScoreControllerTest {

    private static final String URL = "/api/v1/admin/system/search-scores";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminSearchScoreQueryService adminSearchScoreQueryService;

    // SecurityConfig가 요구하는 빈들 — 웹 슬라이스에는 없어 목으로 채운다
    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private GuestIdentityService guestIdentityService;

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
    @DisplayName("로그인하지 않으면 401을 반환한다")
    void 비인증_요청은_401을_반환한다() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("ADMIN이 아니면 403을 반환한다")
    void 일반_회원은_403을_반환한다() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("ADMIN이면 200과 임계값·분포를 반환한다")
    void 관리자는_200을_반환한다() throws Exception {
        when(adminSearchScoreQueryService.getScores(any())).thenReturn(new AdminSearchScoreResponse(
                0.72, 10, 9, 7, 1, 6, List.of(new AdminSearchScoreResponse.Bucket(0.7, 0.75, 3))));

        mockMvc.perform(get(URL).param("from", "2026-10-01T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.threshold").value(0.72))
                .andExpect(jsonPath("$.result.aboveThreshold").value(6))
                .andExpect(jsonPath("$.result.buckets[0].count").value(3));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("시작 시각이 끝 시각보다 늦으면 400과 COMMON400-1을 반환한다")
    void 잘못된_기간은_400을_반환한다() throws Exception {
        mockMvc.perform(get(URL).param("from", "2026-10-04T00:00:00Z").param("to", "2026-10-03T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400-1"))
                .andExpect(jsonPath("$.result.periodValid").exists());
    }
}