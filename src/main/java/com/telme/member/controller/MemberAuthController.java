package com.telme.member.controller;

import com.telme.global.common.CustomResponse;
import com.telme.member.dto.req.LoginRequest;
import com.telme.member.dto.req.SignUpRequest;
import com.telme.member.dto.res.LoginResponse;
import com.telme.member.dto.res.SignUpResponse;
import com.telme.member.service.MemberAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
}
