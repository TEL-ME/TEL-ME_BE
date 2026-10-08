package com.telme.feedback.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.telme.feedback.dto.res.AdminFeedbackDetailResponse;
import com.telme.feedback.dto.res.AdminFeedbackListItemResponse;
import com.telme.feedback.dto.res.AdminFeedbackListResponse;
import com.telme.feedback.service.AdminFeedbackCommandService;
import com.telme.feedback.service.AdminFeedbackQueryService;
import com.telme.global.config.SecurityConfig;
import com.telme.member.repository.UserRepository;
import com.telme.member.service.GuestIdentityService;
import com.telme.member.service.SocialAuthorizationFailureHandler;
import com.telme.member.service.SocialLinkRequestStore;
import com.telme.member.service.SocialLoginFailureHandler;
import com.telme.member.service.SocialLoginSuccessHandler;
import com.telme.member.service.GoogleOidcUserService;
import com.telme.member.service.KakaoOAuth2UserService;
import com.telme.member.service.MemberStatusChecker;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// SecurityConfig 미Import 시 @WebMvcTest가 기본 보안 설정으로 돌아 ADMIN 제한이 검증되지 않음
@WebMvcTest(AdminFeedbackController.class)
@Import({SecurityConfig.class, MemberStatusChecker.class})
class AdminFeedbackControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // MemberStatusFilter가 요청마다 회원을 읽어 SecurityConfig가 이 빈을 요구한다
    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private AdminFeedbackQueryService adminFeedbackQueryService;

    @MockitoBean
    private AdminFeedbackCommandService adminFeedbackCommandService;

    @MockitoBean
    private GuestIdentityService guestIdentityService;

    // SecurityConfig가 securityFilterChain 빈에서 요구하는 OAuth2 로그인 의존성 — 웹 슬라이스에는 없어 목으로 채운다
    @MockitoBean
    private KakaoOAuth2UserService kakaoOAuth2UserService;

    @MockitoBean
    private GoogleOidcUserService googleOidcUserService;

    @MockitoBean
    private SocialLoginSuccessHandler socialLoginSuccessHandler;

    @MockitoBean
    private SocialLoginFailureHandler socialLoginFailureHandler;

    @MockitoBean
    private SocialAuthorizationFailureHandler socialAuthorizationFailureHandler;

    @MockitoBean
    private SocialLinkRequestStore socialLinkRequestStore;

    @Test
    @DisplayName("로그인하지 않으면 401을 반환한다")
    void 비인증_요청은_401을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/admin/feedbacks")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("ADMIN이 아니면 403을 반환한다")
    void 일반_회원은_403을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/admin/feedbacks")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("목록을 조회하면 200과 페이지 정보를 반환한다")
    void 목록을_조회한다() throws Exception {
        when(adminFeedbackQueryService.getDislikes(any())).thenReturn(listResponse());

        mockMvc.perform(get("/api/v1/admin/feedbacks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.feedbacks[0].feedbackId").value(1))
                .andExpect(jsonPath("$.result.totalElements").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("크기가 100을 넘으면 400을 반환한다")
    void 크기_상한을_넘기면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/admin/feedbacks").param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("사유 값이 enum에 없으면 400을 반환한다")
    void 없는_사유는_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/admin/feedbacks").param("reason", "NOPE"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("상세를 조회하면 200과 질문·답변을 반환한다")
    void 상세를_조회한다() throws Exception {
        when(adminFeedbackQueryService.getDislike(anyLong())).thenReturn(detail());

        mockMvc.perform(get("/api/v1/admin/feedbacks/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.question").value("질문입니다."))
                .andExpect(jsonPath("$.result.answer").value("답변입니다."));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("처리 표시를 하면 200과 바뀐 상세를 반환한다")
    void 처리_표시를_한다() throws Exception {
        when(adminFeedbackCommandService.changeHandled(anyLong(), any(), any())).thenReturn(detail());

        mockMvc.perform(put("/api/v1/admin/feedbacks/1/handled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"handled\":true,\"note\":\"처리함\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.feedbackId").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("handled를 빼고 처리 표시를 하면 400을 반환한다")
    void handled가_없으면_400을_반환한다() throws Exception {
        mockMvc.perform(put("/api/v1/admin/feedbacks/1/handled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"처리함\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("메모가 500자를 넘으면 400을 반환한다")
    void 메모가_길면_400을_반환한다() throws Exception {
        mockMvc.perform(put("/api/v1/admin/feedbacks/1/handled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"handled\":true,\"note\":\"" + "가".repeat(501) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    private AdminFeedbackListResponse listResponse() {
        return new AdminFeedbackListResponse(
                List.of(new AdminFeedbackListItemResponse(
                        1L, "WRONG_INFO", "질문입니다.", "틀렸어요", Instant.now(), Instant.now(), false)),
                0, 20, 1, 1);
    }

    private AdminFeedbackDetailResponse detail() {
        return new AdminFeedbackDetailResponse(
                1L, "WRONG_INFO", "틀렸어요", Instant.now(), Instant.now(), false, null, null, null,
                2L, "질문입니다.", "답변입니다.", "GROUNDED", Instant.now(), List.of());
    }
}
