package com.telme.dashboard.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.telme.dashboard.dto.req.AdminIntentDistributionSearchRequest;
import com.telme.dashboard.dto.res.AdminDashboardDailyResponse;
import com.telme.dashboard.dto.res.AdminDashboardResponse;
import com.telme.dashboard.exception.DashboardErrorCode;
import com.telme.global.common.exception.GeneralException;
import com.telme.dashboard.service.AdminDashboardQueryService;
import com.telme.dashboard.service.AdminIntentDistributionQueryService;
import com.telme.dashboard.converter.AdminIntentDistributionConverter;
import java.time.Instant;
import com.telme.global.config.SecurityConfig;
import com.telme.member.repository.UserRepository;
import com.telme.member.service.GuestIdentityService;
import com.telme.member.service.KakaoAccountLinkService;
import com.telme.member.service.KakaoAuthorizationFailureHandler;
import com.telme.member.service.KakaoLinkRequestStore;
import com.telme.member.service.KakaoLoginFailureHandler;
import com.telme.member.service.KakaoLoginSuccessHandler;
import com.telme.member.service.KakaoOAuth2UserService;
import com.telme.member.service.MemberStatusChecker;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// SecurityConfig 미Import 시 @WebMvcTest가 기본 보안 설정으로 돌아 ADMIN 제한이 검증되지 않음
@WebMvcTest(AdminDashboardController.class)
@Import({SecurityConfig.class, MemberStatusChecker.class})
class AdminDashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // MemberStatusFilter가 요청마다 회원을 읽어 SecurityConfig가 이 빈을 요구한다
    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private AdminDashboardQueryService adminDashboardQueryService;

    @MockitoBean
    private AdminIntentDistributionQueryService adminIntentDistributionQueryService;

    @MockitoBean
    private GuestIdentityService guestIdentityService;

    // SecurityConfig가 요구하는 OAuth2 로그인 의존성 — 웹 슬라이스에는 없어 목으로 채운다
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
    void 비인증_요청은_막힌다() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("ADMIN이 아니면 403을 반환한다")
    void 일반_회원은_막힌다() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("기간의 시작이 끝보다 늦으면 400을 반환한다")
    void 거꾸로_된_기간은_400이다() throws Exception {
        when(adminDashboardQueryService.getSummary(any()))
                .thenThrow(new GeneralException(DashboardErrorCode.INVALID_PERIOD));

        mockMvc.perform(get("/api/v1/admin/dashboard")
                        .param("from", "2026-10-02T00:00:00Z").param("to", "2026-10-01T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DASHBOARD400-0"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("ADMIN이면 200과 숫자 다섯 개를 반환한다")
    void 관리자는_요약을_볼_수_있다() throws Exception {
        when(adminDashboardQueryService.getSummary(any()))
                .thenReturn(new AdminDashboardResponse(23L, 7L, 4L, 142L, 127L));

        mockMvc.perform(get("/api/v1/admin/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.unansweredCount").value(23))
                .andExpect(jsonPath("$.result.unhandledFeedbackCount").value(7))
                .andExpect(jsonPath("$.result.failedAnswerCount").value(4))
                .andExpect(jsonPath("$.result.todayQuestionCount").value(142))
                .andExpect(jsonPath("$.result.yesterdayQuestionCount").value(127));
    }
    
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("ADMIN이면 날짜별 질문·오류 수를 날짜 문자열과 함께 반환한다")
    void 관리자는_7일_집계를_볼_수_있다() throws Exception {
        when(adminDashboardQueryService.getDaily()).thenReturn(new AdminDashboardDailyResponse(List.of(
                new AdminDashboardDailyResponse.Day(LocalDate.of(2026, 10, 1), 120L, 3L),
                new AdminDashboardDailyResponse.Day(LocalDate.of(2026, 10, 2), 45L, 0L))));

        mockMvc.perform(get("/api/v1/admin/dashboard/daily"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.days[0].date").value("2026-10-01"))
                .andExpect(jsonPath("$.result.days[0].questionCount").value(120))
                .andExpect(jsonPath("$.result.days[0].errorCount").value(3))
                .andExpect(jsonPath("$.result.days[1].errorCount").value(0));
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("ADMIN이 아니면 7일 집계도 403이다")
    void 일반_회원은_7일_집계를_볼_수_없다() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard/daily")).andExpect(status().isForbidden());
    }

    @Test
    void 분포_비인증_요청은_401이다() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard/intent-distribution"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    void 분포_일반_회원은_403이다() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard/intent-distribution"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void 분포_관리자는_누락된_유형도_0으로_받는다() throws Exception {
        Instant from = Instant.parse("2026-10-01T00:00:00Z");
        Instant to = Instant.parse("2026-10-08T00:00:00Z");
        when(adminIntentDistributionQueryService.getDistribution(any()))
                .thenReturn(new AdminIntentDistributionConverter().toResponse(from, to, List.of()));

        mockMvc.perform(get("/api/v1/admin/dashboard/intent-distribution")
                        .param("from", "2026-10-01T09:00:00+09:00")
                        .param("to", "2026-10-08T09:00:00+09:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.from").value("2026-10-01T00:00:00Z"))
                .andExpect(jsonPath("$.result.totalCount").value(0))
                .andExpect(jsonPath("$.result.intents.length()").value(4))
                .andExpect(jsonPath("$.result.intents[2].intent").value("BOTH"))
                .andExpect(jsonPath("$.result.intents[2].count").value(0))
                .andExpect(jsonPath("$.result.intents[2].percentage").value(0))
                .andExpect(jsonPath("$.result.methods.length()").value(3))
                .andExpect(jsonPath("$.result.methods[2].method").value("UNRECORDED"));

        verify(adminIntentDistributionQueryService).getDistribution(
                new AdminIntentDistributionSearchRequest(from, to));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void 분포_잘못된_시각은_400이다() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard/intent-distribution").param("from", "not-a-date"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400-1"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void 분포_역전된_기간은_400이다() throws Exception {
        when(adminIntentDistributionQueryService.getDistribution(any()))
                .thenThrow(new GeneralException(DashboardErrorCode.INVALID_PERIOD));

        mockMvc.perform(get("/api/v1/admin/dashboard/intent-distribution")
                        .param("from", "2026-10-08T00:00:00Z").param("to", "2026-10-01T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DASHBOARD400-0"));
    }
}
