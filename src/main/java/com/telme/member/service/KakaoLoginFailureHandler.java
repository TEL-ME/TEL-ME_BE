package com.telme.member.service;

import com.telme.member.config.Oauth2Properties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@RequiredArgsConstructor
public class KakaoLoginFailureHandler implements AuthenticationFailureHandler {

    private static final String DEFAULT_REASON = "OAUTH2_LOGIN_FAILED";

    private final Oauth2Properties oauth2Properties;

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        String reason = exception instanceof OAuth2AuthenticationException oauth2Exception
                ? oauth2Exception.getError().getErrorCode()
                : DEFAULT_REASON;

        String redirectUri = UriComponentsBuilder.fromUriString(oauth2Properties.frontendUri() + "/oauth/callback")
                .queryParam("success", false)
                .queryParam("reason", reason)
                .build()
                .encode()
                .toUriString();
        response.sendRedirect(redirectUri);
    }
}
