package com.telme.member.service;

import com.telme.member.config.Oauth2Properties;
import com.telme.member.exception.MemberErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoAuthorizationFailureHandler implements AuthenticationFailureHandler {

    private final Oauth2Properties oauth2Properties;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        String reason = "OAUTH2_LOGIN_FAILED";
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof OAuth2AuthenticationException oauth2Exception
                    && MemberErrorCode.KAKAO_LINK_SESSION_EXPIRED.getCode()
                            .equals(oauth2Exception.getError().getErrorCode())) {
                reason = MemberErrorCode.KAKAO_LINK_SESSION_EXPIRED.getCode();
                break;
            }
        }
        if ("OAUTH2_LOGIN_FAILED".equals(reason)) {
            log.error("카카오 인가 요청 시작 실패", exception);
        }
        response.sendRedirect(UriComponentsBuilder.fromUriString(oauth2Properties.frontendUri() + "/oauth/callback")
                .queryParam("success", false)
                .queryParam("reason", reason)
                .build().encode().toUriString());
    }
}
