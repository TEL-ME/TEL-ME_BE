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
    private final KakaoLinkPendingStore kakaoLinkPendingStore;
    private final PendingKakaoLinkStore pendingKakaoLinkStore;

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        // 카카오 인증 자체가 실패(취소·state 불일치·토큰 교환 실패 등)해도 진행 중이던 pending 정보는 지운다 —
        // 안 지우면 다음 일반 로그인 시도에 이전 연결 모드가 잘못 적용될 수 있다
        kakaoLinkPendingStore.clear(request);
        pendingKakaoLinkStore.clear(request);

        String reason = exception instanceof OAuth2AuthenticationException oauth2Exception
                ? oauth2Exception.getError().getErrorCode()
                : DEFAULT_REASON;

        // 콜백의 error 파라미터 값이 그대로 실릴 수 있어(state만 유효하면 error는 임의로 채울 수 있음) 인코딩 없이 이어붙이지 않는다
        String redirectUri = UriComponentsBuilder.fromUriString(oauth2Properties.frontendUri() + "/oauth/callback")
                .queryParam("success", false)
                .queryParam("reason", reason)
                .build()
                .encode()
                .toUriString();
        response.sendRedirect(redirectUri);
    }
}
