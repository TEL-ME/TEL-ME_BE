package com.telme.chat.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.telme.chat.dto.res.AdminUnansweredListItemResponse;
import com.telme.chat.dto.res.AdminUnansweredListResponse;
import com.telme.chat.service.AdminUnansweredQueryService;
import com.telme.global.config.SecurityConfig;
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

// SecurityConfig 미Import 시 @WebMvcTest가 기본 보안 설정으로 돌아 ADMIN 제한이 검증되지 않음
@WebMvcTest(AdminUnansweredController.class)
@Import({SecurityConfig.class, MemberStatusChecker.class})
class AdminUnansweredControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // MemberStatusFilter가 요청마다 회원을 읽어 SecurityConfig가 이 빈을 요구한다
    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private AdminUnansweredQueryService adminUnansweredQueryService;

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
    @DisplayName("로그인하지 않으면 401을 반환한다")
    void 비인증_요청은_401을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/admin/unanswered")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("ADMIN이 아니면 403을 반환한다")
    void 일반_회원은_403을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/admin/unanswered")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("목록을 조회하면 200과 페이지 정보를 반환한다")
    void 목록을_조회한다() throws Exception {
        when(adminUnansweredQueryService.getUnanswered(any())).thenReturn(listResponse());

        mockMvc.perform(get("/api/v1/admin/unanswered"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.messages[0].messageId").value(1))
                .andExpect(jsonPath("$.result.messages[0].type").value("NO_EVIDENCE"))
                .andExpect(jsonPath("$.result.totalElements").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("유형 값이 enum에 없으면 400을 반환한다")
    void 없는_유형은_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/admin/unanswered").param("type", "NOPE"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("크기가 100을 넘으면 400을 반환한다")
    void 크기_상한을_넘기면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/admin/unanswered").param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    private AdminUnansweredListResponse listResponse() {
        return new AdminUnansweredListResponse(
                List.of(new AdminUnansweredListItemResponse(
                        1L, 10L, "NO_EVIDENCE", "요금제 바꾸고 싶어요", Instant.now())),
                0, 20, 1, 1);
    }
}
