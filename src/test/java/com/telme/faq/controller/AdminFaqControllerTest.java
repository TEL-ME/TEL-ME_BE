package com.telme.faq.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.telme.faq.dto.res.AdminFaqDetailResponse;
import com.telme.faq.dto.res.AdminFaqListItemResponse;
import com.telme.faq.dto.res.AdminFaqListResponse;
import com.telme.faq.service.AdminFaqCommandService;
import com.telme.faq.service.AdminFaqQueryService;
import com.telme.global.config.SecurityConfig;
import com.telme.member.repository.UserRepository;
import com.telme.member.service.MemberStatusChecker;
import com.telme.member.service.GuestIdentityService;
import com.telme.member.service.SocialAuthorizationFailureHandler;
import com.telme.member.service.SocialLinkRequestStore;
import com.telme.member.service.SocialLoginFailureHandler;
import com.telme.member.service.SocialLoginSuccessHandler;
import com.telme.member.service.GoogleOidcUserService;
import com.telme.member.service.KakaoOAuth2UserService;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// SecurityConfig 미Import 시 @WebMvcTest가 기본 보안 설정으로 돌아 ADMIN 제한이 검증되지 않음
@WebMvcTest(AdminFaqController.class)
@Import({SecurityConfig.class, MemberStatusChecker.class})
class AdminFaqControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // MemberStatusFilter가 요청마다 회원을 읽어 SecurityConfig가 이 빈을 요구한다.
    // 슬라이스 테스트의 principal은 문자열이라 필터는 그대로 통과시킨다
    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private AdminFaqQueryService adminFaqQueryService;

    @MockitoBean
    private AdminFaqCommandService adminFaqCommandService;

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
        mockMvc.perform(get("/api/v1/admin/faqs")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("ADMIN이 아니면 403을 반환한다")
    void 일반_회원은_403을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/admin/faqs")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("ADMIN이면 200과 목록을 반환한다")
    void 관리자는_200을_반환한다() throws Exception {
        when(adminFaqQueryService.getFaqs(any())).thenReturn(new AdminFaqListResponse(
                List.of(new AdminFaqListItemResponse(
                        1L, "USIM", "유심 재발급은 어떻게 하나요?", 1, "ACTIVE", 0L, Instant.now())),
                0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/admin/faqs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.faqs[0].faqId").value(1))
                .andExpect(jsonPath("$.result.faqs[0].citationCount").value(0));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("정렬 값이 enum에 없으면 400을 반환한다")
    void 잘못된_정렬_값은_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/admin/faqs").param("sort", "UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400-1"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("ADMIN이면 등록에 201과 만들어진 FAQ를 반환한다")
    void 관리자는_등록할_수_있다() throws Exception {
        when(adminFaqCommandService.create(any(), any())).thenReturn(detail());

        mockMvc.perform(postFaq(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.result.faqId").value(1))
                .andExpect(jsonPath("$.result.version").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("ADMIN이면 수정에 200과 고친 FAQ를 반환한다")
    void 관리자는_수정할_수_있다() throws Exception {
        when(adminFaqCommandService.update(any(Long.class), any(), any())).thenReturn(detail());

        mockMvc.perform(putFaq(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.faqId").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("ADMIN이면 삭제에 200을 반환한다")
    void 관리자는_삭제할_수_있다() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/faqs/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true));
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("ADMIN이 아니면 등록·수정·삭제가 모두 403이다")
    void 일반_회원은_쓰기가_막힌다() throws Exception {
        mockMvc.perform(postFaq(VALID_BODY)).andExpect(status().isForbidden());
        mockMvc.perform(putFaq(VALID_BODY)).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/admin/faqs/1")).andExpect(status().isForbidden());
        mockMvc.perform(putStatus(STATUS_BODY)).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/admin/faqs/1/permanent")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("로그인하지 않으면 등록·수정·삭제가 모두 401이다")
    void 비인증_요청은_쓰기가_막힌다() throws Exception {
        mockMvc.perform(postFaq(VALID_BODY)).andExpect(status().isUnauthorized());
        mockMvc.perform(putFaq(VALID_BODY)).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/admin/faqs/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(putStatus(STATUS_BODY)).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/admin/faqs/1/permanent")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("질문이 비어 있으면 400을 반환한다")
    void 질문이_없으면_400을_반환한다() throws Exception {
        mockMvc.perform(postFaq("""
                {"category":"SERVICE","question":"  ","answer":"답변입니다."}
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400-1"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("등록 카테고리가 10종에 없으면 400을 반환한다")
    void 등록_카테고리가_잘못되면_400을_반환한다() throws Exception {
        mockMvc.perform(postFaq("""
                {"category":"usim","question":"질문입니다.","answer":"답변입니다."}
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400-0"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("카테고리가 10종에 없으면 빈 목록이 아니라 400을 반환한다")
    void 잘못된_카테고리는_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/admin/faqs").param("category", "usim"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400-1"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("size가 100을 넘으면 400을 반환한다")
    void size가_너무_크면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/admin/faqs").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400-1"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("ADMIN이면 상태 변경에 200과 바뀐 FAQ를 반환한다")
    void 관리자는_상태를_바꿀_수_있다() throws Exception {
        when(adminFaqCommandService.changeStatus(any(Long.class), any(), any())).thenReturn(detail());

        mockMvc.perform(putStatus(STATUS_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.faqId").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("ADMIN이면 영구 삭제에 200을 반환한다")
    void 관리자는_영구_삭제할_수_있다() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/faqs/1/permanent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("상태를 주지 않거나 3종에 없으면 400을 반환한다")
    void 잘못된_상태는_400을_반환한다() throws Exception {
        mockMvc.perform(putStatus("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400-1"));
        mockMvc.perform(putStatus("""
                {"status":"active"}
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400-0"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("상태 변경으로는 삭제할 수 없어 DELETED는 400을 반환한다")
    void 상태_변경으로는_삭제할_수_없다() throws Exception {
        mockMvc.perform(putStatus("""
                {"status":"DELETED"}
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400-0"));
    }

    private static final String VALID_BODY = """
            {"category":"SERVICE","question":"질문입니다.","answer":"답변입니다."}
            """;

    private static final String STATUS_BODY = """
            {"status":"ACTIVE"}
            """;

    private MockHttpServletRequestBuilder putStatus(String body) {
        return put("/api/v1/admin/faqs/1/status")
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private MockHttpServletRequestBuilder postFaq(String body) {
        return post("/api/v1/admin/faqs")
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private MockHttpServletRequestBuilder putFaq(String body) {
        return put("/api/v1/admin/faqs/1")
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private AdminFaqDetailResponse detail() {
        return new AdminFaqDetailResponse(
                1L, "SERVICE", "질문입니다.", "답변입니다.", null, 1, 0, "hash", "ACTIVE",
                0L, 2L, 2L, Instant.now(), Instant.now());
    }
}
