package com.telme.llm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.telme.global.common.exception.GeneralException;
import com.telme.global.config.SecurityConfig;
import com.telme.llm.dto.res.AdminLatencyResponse;
import com.telme.llm.exception.LlmErrorCode;
import com.telme.llm.service.AdminLatencyQueryService;
import com.telme.member.repository.UserRepository;
import com.telme.member.service.GuestIdentityService;
import com.telme.member.service.KakaoAuthorizationFailureHandler;
import com.telme.member.service.KakaoLinkRequestStore;
import com.telme.member.service.KakaoLoginFailureHandler;
import com.telme.member.service.KakaoLoginSuccessHandler;
import com.telme.member.service.KakaoOAuth2UserService;
import com.telme.member.service.MemberStatusChecker;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminLatencyController.class)
@Import({SecurityConfig.class, MemberStatusChecker.class})
class AdminLatencyControllerTest {

    private static final String URL = "/api/v1/admin/system/latency";
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockitoBean
    private AdminLatencyQueryService adminLatencyQueryService;
    
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
    @DisplayName("ADMIN이면 200과 전체·첫 토큰·작업별 응답 속도를 반환한다")
    void 관리자는_200을_반환한다() throws Exception {
        when(adminLatencyQueryService.getLatency(any())).thenReturn(new AdminLatencyResponse(
                Instant.parse("2026-10-03T00:00:00Z"), Instant.parse("2026-10-04T00:00:00Z"),
                new AdminLatencyResponse.Stats(10, 4200L, 3900L, 8100L),
                new AdminLatencyResponse.Stats(8, 800L, 750L, 1500L),
                List.of(new AdminLatencyResponse.TaskStats("ROUTING", 10, 600L, 550L, 1200L))));
        
        mockMvc.perform(get(URL)).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.overall.p95Ms").value(8100))
                .andExpect(jsonPath("$.result.firstToken.avgMs").value(800))
                .andExpect(jsonPath("$.result.tasks[0].taskType").value("ROUTING"));
    }
    
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("시작 시각이 끝 시각보다 늦으면 400과 LLM400-0을 반환한다")
    void 거꾸로_된_기간은_400이다() throws Exception {
        when(adminLatencyQueryService.getLatency(any()))
                .thenThrow(new GeneralException(LlmErrorCode.INVALID_PERIOD));

        mockMvc.perform(get(URL).param("from", "2026-10-04T00:00:00Z").param("to", "2026-10-03T00:00:00Z"))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.code").value("LLM400-0"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("시각 형식이 틀리면 400과 COMMON400-1을 반환한다")
    void 잘못된_시각_형식은_400이다() throws Exception {
        mockMvc.perform(get(URL).param("from", "어제"))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.code").value("COMMON400-1"));
    }
}
