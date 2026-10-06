package com.telme.member.controller;

import com.telme.member.repository.UserRepository;
import com.telme.member.service.MemberStatusChecker;
import com.telme.member.service.KakaoLinkRequestStore;
import com.telme.member.service.KakaoAuthorizationFailureHandler;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.global.common.exception.GeneralException;
import com.telme.global.config.SecurityConfig;
import com.telme.member.dto.res.LoginResponse;
import com.telme.member.dto.res.MemberMeResponse;
import com.telme.member.dto.res.MemberMeResponse.LoginMethod;
import com.telme.member.dto.res.SignUpResponse;
import com.telme.member.exception.MemberErrorCode;
import com.telme.member.service.EmailLoginMethodService;
import com.telme.member.service.GoogleOidcUserService;
import com.telme.member.service.GuestIdentityService;
import com.telme.member.service.SocialAccountLinkService;
import com.telme.member.service.KakaoLinkStartService;
import com.telme.member.service.KakaoLoginFailureHandler;
import com.telme.member.service.KakaoLoginSuccessHandler;
import com.telme.member.service.KakaoOAuth2UserService;
import com.telme.member.service.MemberAuthService;
import com.telme.member.service.MemberProfileService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// SecurityConfig 미Import 시 @WebMvcTest가 기본 보안 설정으로 돌아 permitAll이 검증되지 않음
@WebMvcTest(MemberAuthController.class)
@Import({SecurityConfig.class, MemberStatusChecker.class})
class MemberAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // MemberStatusFilter가 요청마다 회원을 읽어 SecurityConfig가 이 빈을 요구한다.
    // 슬라이스 테스트의 principal은 문자열이라 필터는 그대로 통과시킨다
    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private KakaoAuthorizationFailureHandler kakaoAuthorizationFailureHandler;

    @MockitoBean
    private KakaoLinkRequestStore kakaoLinkRequestStore;

    @MockitoBean
    private MemberAuthService memberAuthService;

    @MockitoBean
    private MemberProfileService memberProfileService;

    @MockitoBean
    private SocialAccountLinkService socialAccountLinkService;

    @MockitoBean
    private EmailLoginMethodService emailLoginMethodService;

    @MockitoBean
    private KakaoLinkStartService kakaoLinkStartService;

    @MockitoBean
    private GuestIdentityService guestIdentityService;

    // SecurityConfig가 securityFilterChain 빈에서 요구하는 OAuth2 로그인 의존성 — 웹 슬라이스에는 없어 목으로 채운다
    @MockitoBean
    private KakaoOAuth2UserService kakaoOAuth2UserService;

    @MockitoBean
    private GoogleOidcUserService googleOidcUserService;

    @MockitoBean
    private KakaoLoginSuccessHandler kakaoLoginSuccessHandler;

    @MockitoBean
    private KakaoLoginFailureHandler kakaoLoginFailureHandler;

    @Test
    @DisplayName("미인증 상태의 현재 회원 조회는 GUEST 정보를 반환한다")
    void 현재_회원_조회_미인증이면_GUEST() throws Exception {
        when(memberProfileService.getMe(any())).thenReturn(MemberMeResponse.guest());

        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.authenticated").value(false))
                .andExpect(jsonPath("$.result.role").value("GUEST"))
                .andExpect(jsonPath("$.result.userId").doesNotExist())
                .andExpect(jsonPath("$.result.loginMethods").isEmpty());
    }

    @Test
    @DisplayName("인증된 현재 회원의 기본 정보와 로그인 수단을 반환한다")
    void 현재_회원과_로그인_수단_조회() throws Exception {
        when(memberProfileService.getMe(any())).thenReturn(new MemberMeResponse(
                true, 7L, "member@example.com", "회원이름", MemberMeResponse.Role.ADMIN,
                java.util.List.of(LoginMethod.EMAIL, LoginMethod.KAKAO)));

        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.authenticated").value(true))
                .andExpect(jsonPath("$.result.userId").value(7))
                .andExpect(jsonPath("$.result.email").value("member@example.com"))
                .andExpect(jsonPath("$.result.name").value("회원이름"))
                .andExpect(jsonPath("$.result.role").value("ADMIN"))
                .andExpect(jsonPath("$.result.loginMethods[0]").value("EMAIL"))
                .andExpect(jsonPath("$.result.loginMethods[1]").value("KAKAO"));
    }

    @Test
    void 인가_시작_실패는_전용_핸들러로_전달한다() throws Exception {
        org.mockito.Mockito.doThrow(new GeneralException(MemberErrorCode.SOCIAL_LINK_SESSION_EXPIRED))
                .when(kakaoLinkRequestStore).bind(any(), any(), any());

        mockMvc.perform(get("/oauth2/authorization/kakao").param("link_token", "invalid-token"));

        org.mockito.Mockito.verify(kakaoAuthorizationFailureHandler).onAuthenticationFailure(any(), any(), any());
    }

    @Test
    @DisplayName("Google 로그인 시작 경로는 Google 인가 화면으로 리다이렉트한다")
    void 구글_로그인_시작() throws Exception {
        mockMvc.perform(get("/oauth2/authorization/google"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string(
                        "Location", startsWith("https://accounts.google.com/o/oauth2/v2/auth?")));
    }

    @Test
    @DisplayName("정상 요청이면 200과 가입 결과를 반환한다")
    void 정상_요청이면_200을_반환한다() throws Exception {
        when(memberAuthService.signUp(any(), any(), any())).thenReturn(new SignUpResponse(1L, "new@example.com"));

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new SignUpRequestJson("테스트", "new@example.com", "password123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.userId").value(1))
                .andExpect(jsonPath("$.result.email").value("new@example.com"));
    }

    @Test
    @DisplayName("이름이 비어 있거나 공백뿐이면 400을 반환한다")
    void 이름이_비어_있으면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new SignUpRequestJson("   ", "new@example.com", "password123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.result.name").value("이름을 입력해 주세요."));
    }

    @Test
    @DisplayName("이름이 50자를 초과하면 400을 반환한다")
    void 이름이_50자를_초과하면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new SignUpRequestJson("가".repeat(51), "new@example.com", "password123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.result.name").value("이름은 50자 이하여야 합니다."));
    }

    @Test
    @DisplayName("이메일 형식이 아니면 400을 반환한다")
    void 이메일_형식_오류면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new SignUpRequestJson("테스트", "not-an-email", "password123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.result.email").value("올바른 이메일 형식으로 입력해 주세요."));
    }

    @Test
    @DisplayName("이메일 형식은 맞지만 255자를 초과하면 400을 반환한다")
    void 이메일이_255자를_초과하면_400을_반환한다() throws Exception {
        // 지나치게 긴 로컬파트/라벨은 @Email 자체가 형식 오류로 걸러내므로, 각 라벨은 짧게 유지하면서
        // 라벨 개수로 전체 길이만 255자를 넘기는(=@Email은 통과하지만 @Size(max=255)에만 걸리는) 값을 쓴다
        String domain = String.join(".", "x".repeat(62), "x".repeat(62), "x".repeat(62), "x".repeat(62));
        String tooLongEmail = "user@" + domain;

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new SignUpRequestJson("테스트", tooLongEmail, "password123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.result.email").value("이메일은 255자 이하여야 합니다."));
    }

    @Test
    @DisplayName("비밀번호가 8자 미만이면 400을 반환한다")
    void 비밀번호가_짧으면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new SignUpRequestJson("테스트", "new@example.com", "short"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.result.password").value("비밀번호는 8자 이상이어야 합니다."));
    }

    @Test
    @DisplayName("가입 필수값이 없으면 필드별 한글 메시지를 반환한다")
    void 가입_필수값이_없으면_한글_메시지를_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new SignUpRequestJson(null, null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.result.name").value("이름을 입력해 주세요."))
                .andExpect(jsonPath("$.result.email").value("이메일을 입력해 주세요."))
                .andExpect(jsonPath("$.result.password").value("비밀번호를 입력해 주세요."));
    }

    @Test
    @DisplayName("비밀번호가 UTF-8 기준 72바이트면 가입할 수 있다")
    void 비밀번호가_72바이트면_가입된다() throws Exception {
        String password72Bytes = "a".repeat(72);
        when(memberAuthService.signUp(any(), any(), any())).thenReturn(new SignUpResponse(1L, "new@example.com"));

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new SignUpRequestJson("테스트", "new@example.com", password72Bytes))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("비밀번호가 UTF-8 기준 72바이트를 넘으면 400과 password 필드 오류를 반환한다")
    void 비밀번호가_72바이트를_넘으면_400을_반환한다() throws Exception {
        String password73Bytes = "a".repeat(73);

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new SignUpRequestJson("테스트", "new@example.com", password73Bytes))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.result.password")
                        .value("비밀번호는 UTF-8 기준 72바이트를 넘을 수 없습니다."));
    }

    @Test
    @DisplayName("문자 수는 적어도 멀티바이트 문자로 72바이트를 넘으면 400을 반환한다")
    void 멀티바이트_비밀번호가_72바이트를_넘으면_400을_반환한다() throws Exception {
        // 한글 1자는 UTF-8로 3바이트 — 25자(75바이트)는 문자 수는 적지만 바이트 제한은 넘는다
        String password25Korean = "가".repeat(25);

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new SignUpRequestJson("테스트", "new@example.com", password25Korean))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("이메일이 이미 존재하면 409와 에러 코드를 반환한다")
    void 이메일_중복이면_409를_반환한다() throws Exception {
        when(memberAuthService.signUp(any(), any(), any()))
                .thenThrow(new GeneralException(MemberErrorCode.EMAIL_ALREADY_EXISTS));

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new SignUpRequestJson("테스트", "dup@example.com", "password123"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("MEMBER409-0"));
    }

    @Test
    @DisplayName("가입 요청은 인증 없이도(permitAll) 호출할 수 있다")
    void 가입_요청도_permitAll로_호출된다() throws Exception {
        when(memberAuthService.signUp(any(), any(), any())).thenReturn(new SignUpResponse(1L, "new@example.com"));

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new SignUpRequestJson("테스트", "new@example.com", "password123"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("정상 로그인이면 200과 로그인 결과를 반환한다")
    void 정상_로그인이면_200을_반환한다() throws Exception {
        when(memberAuthService.login(any(), any(), any())).thenReturn(new LoginResponse(1L, "login@example.com"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LoginRequestJson("login@example.com", "password123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.userId").value(1));
    }

    @Test
    @DisplayName("로그인 이메일 형식이 아니면 400을 반환한다")
    void 로그인_이메일_형식_오류면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LoginRequestJson("not-an-email", "password123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.result.email").value("올바른 이메일 형식으로 입력해 주세요."));
    }

    @Test
    @DisplayName("로그인 이메일이 255자를 초과하면 400을 반환한다")
    void 로그인_이메일이_255자를_초과하면_400을_반환한다() throws Exception {
        String domain = String.join(".", "x".repeat(62), "x".repeat(62), "x".repeat(62), "x".repeat(62));
        String tooLongEmail = "user@" + domain;

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LoginRequestJson(tooLongEmail, "password123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.result.email").value("이메일은 255자 이하여야 합니다."));
    }

    @Test
    @DisplayName("로그인 비밀번호가 UTF-8 기준 72바이트를 넘으면 400을 반환한다")
    void 로그인_비밀번호가_72바이트를_넘으면_400을_반환한다() throws Exception {
        String password73Bytes = "a".repeat(73);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LoginRequestJson("login@example.com", password73Bytes))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.result.password")
                        .value("비밀번호는 UTF-8 기준 72바이트를 넘을 수 없습니다."));
    }

    @Test
    @DisplayName("로그인 필수값이 없으면 필드별 한글 메시지를 반환한다")
    void 로그인_필수값이_없으면_한글_메시지를_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LoginRequestJson(null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.result.email").value("이메일을 입력해 주세요."))
                .andExpect(jsonPath("$.result.password").value("비밀번호를 입력해 주세요."));
    }

    @Test
    @DisplayName("로그인 실패면 401과 에러 코드를 반환한다")
    void 로그인_실패면_401을_반환한다() throws Exception {
        when(memberAuthService.login(any(), any(), any()))
                .thenThrow(new GeneralException(MemberErrorCode.INVALID_CREDENTIALS));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LoginRequestJson("login@example.com", "wrong"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MEMBER401-0"));
    }

    @Test
    @DisplayName("정지된 계정으로 로그인하면 403과 에러 코드를 반환한다")
    void 정지된_계정이면_403을_반환한다() throws Exception {
        when(memberAuthService.login(any(), any(), any()))
                .thenThrow(new GeneralException(MemberErrorCode.ACCOUNT_SUSPENDED));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LoginRequestJson("login@example.com", "password123"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MEMBER403-0"));
    }

    @Test
    @DisplayName("탈퇴한 계정으로 로그인하면 403과 에러 코드를 반환한다")
    void 탈퇴한_계정이면_403을_반환한다() throws Exception {
        when(memberAuthService.login(any(), any(), any()))
                .thenThrow(new GeneralException(MemberErrorCode.ACCOUNT_WITHDRAWN));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LoginRequestJson("login@example.com", "password123"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MEMBER403-1"));
    }

    @Test
    @DisplayName("로그인 요청은 인증 없이도(permitAll) 호출할 수 있다")
    void 로그인_요청도_permitAll로_호출된다() throws Exception {
        when(memberAuthService.login(any(), any(), any())).thenReturn(new LoginResponse(1L, "login@example.com"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LoginRequestJson("login@example.com", "password123"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("로그아웃 요청은 인증 없이도(permitAll) 호출되고 200을 반환한다")
    void 로그아웃_요청도_permitAll로_호출된다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true));
    }

    @Test
    @DisplayName("이메일 로그인 방법 추가는 인증 없이 호출하면 401과 다른 API 오류와 같은 형식의 JSON을 반환한다")
    void 이메일_로그인_방법_추가는_미인증이면_401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login-methods/email")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new EmailLoginMethodRequestJson("kakao@example.com", "password123"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value(MemberErrorCode.UNAUTHENTICATED.getCode()));
    }

    @Test
    @WithMockUser
    @DisplayName("이메일 로그인 방법 추가는 인증된 상태면 호출할 수 있다")
    void 이메일_로그인_방법_추가는_인증되면_호출된다() throws Exception {
        when(emailLoginMethodService.addEmailLogin(any(), any())).thenReturn(new SignUpResponse(1L, "kakao@example.com"));

        mockMvc.perform(post("/api/v1/auth/login-methods/email")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new EmailLoginMethodRequestJson("kakao@example.com", "password123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.email").value("kakao@example.com"));
    }

    @Test
    @WithMockUser
    @DisplayName("이메일 로그인 방법 추가의 필수값이 없으면 필드별 한글 메시지를 반환한다")
    void 이메일_로그인_방법_추가_필수값이_없으면_한글_메시지를_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login-methods/email")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new EmailLoginMethodRequestJson(null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.result.email").value("이메일을 입력해 주세요."))
                .andExpect(jsonPath("$.result.password").value("비밀번호를 입력해 주세요."));
    }

    @Test
    @DisplayName("소셜 계정 연결 비밀번호가 없으면 한글 메시지를 반환한다")
    void 소셜_계정_연결_비밀번호가_없으면_한글_메시지를_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/social/link")
                        .contentType("application/json")
                        .content("{\"password\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.result.password").value("비밀번호를 입력해 주세요."));
    }

    @Test
    @DisplayName("카카오 연결 시작은 인증 없이 호출하면 401과 다른 API 오류와 같은 형식의 JSON을 반환한다")
    void 카카오_연결_시작은_미인증이면_401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/kakao/link-start"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value(MemberErrorCode.UNAUTHENTICATED.getCode()));
    }

    @Test
    @WithMockUser
    @DisplayName("카카오 연결 시작은 인증된 상태면 카카오 인증 화면으로 리다이렉트한다")
    void 카카오_연결_시작은_인증되면_리다이렉트된다() throws Exception {
        when(kakaoLinkStartService.start(any())).thenReturn("/oauth2/authorization/kakao");

        mockMvc.perform(get("/api/v1/auth/kakao/link-start"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/oauth2/authorization/kakao"));
    }

    private record SignUpRequestJson(String name, String email, String password) {
    }

    private record EmailLoginMethodRequestJson(String email, String password) {
    }

    private record LoginRequestJson(String email, String password) {
    }
}
