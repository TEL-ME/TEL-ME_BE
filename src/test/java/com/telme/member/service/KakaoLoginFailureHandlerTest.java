package com.telme.member.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.telme.member.config.Oauth2Properties;
import java.time.Clock;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

class KakaoLoginFailureHandlerTest {

    private final Oauth2Properties oauth2Properties = new Oauth2Properties("http://localhost:3000");
    private final Clock clock = Clock.systemDefaultZone().withZone(ZoneOffset.UTC);
    private final KakaoLinkRequestStore kakaoLinkRequestStore = new KakaoLinkRequestStore(clock);
    private final KakaoEmailMatchStore kakaoEmailMatchStore = new KakaoEmailMatchStore(clock);
    private final KakaoLoginFailureHandler handler =
            new KakaoLoginFailureHandler(oauth2Properties, kakaoLinkRequestStore, kakaoEmailMatchStore);

    @Test
    @DisplayName("OAuth2AuthenticationException이면 오류 코드를 reason으로 실어 리다이렉트한다")
    void OAuth2예외는_오류코드를_reason으로() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        OAuth2AuthenticationException exception =
                new OAuth2AuthenticationException(new OAuth2Error("MEMBER403-0", "이용이 제한된 계정입니다.", null));

        handler.onAuthenticationFailure(request, response, exception);

        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost:3000/oauth/callback?success=false&reason=MEMBER403-0");
    }

    @Test
    @DisplayName("그 외 인증 예외는 기본 reason으로 리다이렉트한다")
    void 그외_예외는_기본_reason으로() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(request, response, new BadCredentialsException("실패"));

        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost:3000/oauth/callback?success=false&reason=OAUTH2_LOGIN_FAILED");
    }

    @Test
    @DisplayName("오류 코드에 &, = 같은 쿼리 구조 문자가 있어도 URL 인코딩되어 쿼리가 깨지지 않는다")
    void 오류코드에_특수문자가_있어도_인코딩된다() throws Exception {
        // 콜백의 error 파라미터는 신뢰할 수 없는 값이라 임의로 채운 콜백을 흉내낸다
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        OAuth2AuthenticationException exception =
                new OAuth2AuthenticationException(new OAuth2Error("a&b=c evil", "설명", null));

        handler.onAuthenticationFailure(request, response, exception);

        String redirectedUrl = response.getRedirectedUrl();
        assertThat(redirectedUrl).doesNotContain("a&b=c evil");
        assertThat(redirectedUrl).startsWith("http://localhost:3000/oauth/callback?success=false&reason=a%26b%3Dc");
    }

    @Test
    @DisplayName("카카오 인증 자체가 실패해도 진행 중이던 연결/이메일-연결 pending 정보를 모두 지운다")
    void 인증_실패시_pending_정보를_모두_지운다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        kakaoLinkRequestStore.issue(request, 30L);
        kakaoEmailMatchStore.issue(request, "kakao-1", 10L, "match@example.com");

        handler.onAuthenticationFailure(request, response, new BadCredentialsException("카카오 인증 취소"));

        assertThat(request.getSession(false).getAttribute(KakaoLinkRequestStore.SESSION_ATTRIBUTE)).isNull();
        assertThat(request.getSession(false).getAttribute(KakaoEmailMatchStore.SESSION_ATTRIBUTE)).isNull();
    }
}
