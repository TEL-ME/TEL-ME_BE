package com.telme.llm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.telme.global.common.exception.GeneralException;
import com.telme.global.config.SecurityConfig;
import com.telme.llm.dto.res.AdminLlmErrorListItemResponse;
import com.telme.llm.dto.res.AdminLlmErrorListResponse;
import com.telme.llm.exception.LlmErrorCode;
import com.telme.llm.service.AdminLlmErrorQueryService;
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

@WebMvcTest(AdminLlmErrorController.class)
@Import({SecurityConfig.class, MemberStatusChecker.class})
class AdminLlmErrorControllerTest {

    private static final String URL = "/api/v1/admin/system/errors";
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockitoBean
    private AdminLlmErrorQueryService adminLlmErrorQueryService;
    
    // SecurityConfig가 요구하는 빈들
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
    @DisplayName("ADMIN이면 200과 오류 목록을 반환한다")
    void 관리자는_200을_반환한다() throws Exception {
        when(adminLlmErrorQueryService.getErrors(any()))
        .thenReturn(new AdminLlmErrorListResponse(
                List.of(new AdminLlmErrorListItemResponse(10L, Instant.now(), "TIMEOUT", 
                        "RAG_ANSWER", 2, "qwen", 30000, "LLM 응답 시간이 초과되었습니다.", 1L)), 0, 20, 1, 1));
        mockMvc.perform(get(URL))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.result.errors[0].errorType").value("TIMEOUT"))
               .andExpect(jsonPath("$.result.errors[0].attempt").value(2))
               .andExpect(jsonPath("$.result.errors[0].errorMessage").value("LLM 응답 시간이 초과되었습니다."));
    }
    
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("오류·작업 종류가 enum에 없거나 페이지 범위를 벗어나면 400과 COMMON400-1을 반환한다")
    void 잘못된_요청은_400을_반환한다() throws Exception {
        String[][] invalid = {{"errorType", "SUCCESS"}, {"taskType", "UNKNOWN"}, {"size", "101"}, {"page", "-1"}};
        
        for (String[] param : invalid) {
            mockMvc.perform(get(URL).param(param[0], param[1]))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("COMMON400-1"));
        }
    }
    
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("기간의 시작이 끝보다 늦으면 400과 LLM400-0을 반환한다")
    void 거꾸로_된_기간은_400이다() throws Exception {
        when(adminLlmErrorQueryService.getErrors(any())).thenThrow(new GeneralException(LlmErrorCode.INVALID_PERIOD));
        
        mockMvc.perform(get(URL).param("from", "2026-10-02T00:00:00Z").param("to", "2026-10-01T00:00:00Z"))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.code").value("LLM400-0"));
    }
}
