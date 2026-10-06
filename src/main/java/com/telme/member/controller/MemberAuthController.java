package com.telme.member.controller;

import com.telme.global.common.CustomResponse;
import com.telme.member.dto.req.EmailLoginMethodRequest;
import com.telme.member.dto.req.SocialLinkConfirmRequest;
import com.telme.member.dto.req.LoginRequest;
import com.telme.member.dto.req.SignUpRequest;
import com.telme.member.dto.res.LoginResponse;
import com.telme.member.dto.res.MemberMeResponse;
import com.telme.member.dto.res.SignUpResponse;
import com.telme.member.service.EmailLoginMethodService;
import com.telme.member.service.SocialAccountLinkService;
import com.telme.member.service.KakaoLinkStartService;
import com.telme.member.service.MemberAuthService;
import com.telme.member.service.MemberProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Member Auth", description = "이메일 회원가입/로그인/로그아웃")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class MemberAuthController {

    private final MemberAuthService memberAuthService;
    private final SocialAccountLinkService socialAccountLinkService;
    private final EmailLoginMethodService emailLoginMethodService;
    private final KakaoLinkStartService kakaoLinkStartService;
    private final MemberProfileService memberProfileService;

    @Operation(summary = "현재 회원 및 로그인 수단 조회", description = "미인증 상태에서는 GUEST 정보를 반환한다.")
    @GetMapping("/me")
    public CustomResponse<MemberMeResponse> getMe(HttpServletRequest httpRequest) {
        return CustomResponse.onSuccess(memberProfileService.getMe(httpRequest));
    }

    @Operation(summary = "이메일 회원가입", description = "가입 성공 시 자동으로 로그인 처리된다.")
    @PostMapping("/signup")
    public CustomResponse<SignUpResponse> signUp(
            @Valid @RequestBody SignUpRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        return CustomResponse.onSuccess(memberAuthService.signUp(request, httpRequest, httpResponse));
    }

    @Operation(summary = "이메일 로그인")
    @PostMapping("/login")
    public CustomResponse<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        return CustomResponse.onSuccess(memberAuthService.login(request, httpRequest, httpResponse));
    }

    @Operation(summary = "로그아웃")
    @PostMapping("/logout")
    public CustomResponse<Void> logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        memberAuthService.logout(httpRequest, httpResponse);
        return CustomResponse.onSuccess(null);
    }

    @Operation(summary = "소셜 계정을 기존 이메일 계정에 연결",
            description = "소셜 로그인 중 이메일이 일치하는 기존 계정을 발견했을 때(reason=MEMBER409-1), "
                    + "비밀번호 확인 후 인증한 소셜 로그인을 연결하고 로그인 처리한다. /kakao/link는 기존 호환 경로다.")
    @PostMapping({"/social/link", "/kakao/link"})
    public CustomResponse<LoginResponse> confirmSocialLink(
            @Valid @RequestBody SocialLinkConfirmRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        return CustomResponse.onSuccess(socialAccountLinkService.confirmLink(request, httpRequest, httpResponse));
    }

    @Operation(summary = "이메일 로그인 방법 추가", description = "카카오 등 소셜로만 가입한 회원이 이메일·비밀번호 로그인도 쓸 수 있게 등록한다. 로그인 상태에서만 호출 가능.")
    @PostMapping("/login-methods/email")
    public CustomResponse<SignUpResponse> addEmailLoginMethod(
            @Valid @RequestBody EmailLoginMethodRequest request,
            HttpServletRequest httpRequest
    ) {
        return CustomResponse.onSuccess(emailLoginMethodService.addEmailLogin(request, httpRequest));
    }

    @Operation(summary = "이메일 계정에 카카오 로그인 연결 시작", description = "로그인 상태에서만 호출 가능. 카카오 인증 화면으로 리다이렉트한다.")
    @GetMapping("/kakao/link-start")
    public void startKakaoLink(HttpServletRequest httpRequest, HttpServletResponse httpResponse) throws IOException {
        httpResponse.sendRedirect(kakaoLinkStartService.start(httpRequest));
    }
}
