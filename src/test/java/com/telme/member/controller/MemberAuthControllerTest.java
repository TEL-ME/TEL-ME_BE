package com.telme.member.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.global.common.exception.GeneralException;
import com.telme.global.config.SecurityConfig;
import com.telme.member.dto.res.LoginResponse;
import com.telme.member.dto.res.SignUpResponse;
import com.telme.member.exception.MemberErrorCode;
import com.telme.member.service.GuestIdentityService;
import com.telme.member.service.MemberAuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// SecurityConfig 미Import 시 @WebMvcTest가 기본 보안 설정으로 돌아 permitAll이 검증되지 않음
@WebMvcTest(MemberAuthController.class)
@Import(SecurityConfig.class)
class MemberAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private MemberAuthService memberAuthService;

    @MockitoBean
    private GuestIdentityService guestIdentityService;

    @Test
    @DisplayName("정상 요청이면 200과 가입 결과를 반환한다")
    void 정상_요청이면_200을_반환한다() throws Exception {
        when(memberAuthService.signUp(any(), any(), any())).thenReturn(new SignUpResponse(1L, "new@example.com"));

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new SignUpRequestJson("new@example.com", "password123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.userId").value(1))
                .andExpect(jsonPath("$.result.email").value("new@example.com"));
    }

    @Test
    @DisplayName("이메일 형식이 아니면 400을 반환한다")
    void 이메일_형식_오류면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new SignUpRequestJson("not-an-email", "password123"))))
                .andExpect(status().isBadRequest());
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
                        .content(objectMapper.writeValueAsString(new SignUpRequestJson(tooLongEmail, "password123"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("비밀번호가 8자 미만이면 400을 반환한다")
    void 비밀번호가_짧으면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new SignUpRequestJson("new@example.com", "short"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("비밀번호가 UTF-8 기준 72바이트면 가입할 수 있다")
    void 비밀번호가_72바이트면_가입된다() throws Exception {
        String password72Bytes = "a".repeat(72);
        when(memberAuthService.signUp(any(), any(), any())).thenReturn(new SignUpResponse(1L, "new@example.com"));

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new SignUpRequestJson("new@example.com", password72Bytes))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("비밀번호가 UTF-8 기준 72바이트를 넘으면 400과 password 필드 오류를 반환한다")
    void 비밀번호가_72바이트를_넘으면_400을_반환한다() throws Exception {
        String password73Bytes = "a".repeat(73);

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new SignUpRequestJson("new@example.com", password73Bytes))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.result.password").exists());
    }

    @Test
    @DisplayName("문자 수는 적어도 멀티바이트 문자로 72바이트를 넘으면 400을 반환한다")
    void 멀티바이트_비밀번호가_72바이트를_넘으면_400을_반환한다() throws Exception {
        // 한글 1자는 UTF-8로 3바이트 — 25자(75바이트)는 문자 수는 적지만 바이트 제한은 넘는다
        String password25Korean = "가".repeat(25);

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new SignUpRequestJson("new@example.com", password25Korean))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("이메일이 이미 존재하면 409와 에러 코드를 반환한다")
    void 이메일_중복이면_409를_반환한다() throws Exception {
        when(memberAuthService.signUp(any(), any(), any()))
                .thenThrow(new GeneralException(MemberErrorCode.EMAIL_ALREADY_EXISTS));

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new SignUpRequestJson("dup@example.com", "password123"))))
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
                        .content(objectMapper.writeValueAsString(new SignUpRequestJson("new@example.com", "password123"))))
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

    private record SignUpRequestJson(String email, String password) {
    }

    private record LoginRequestJson(String email, String password) {
    }
}
