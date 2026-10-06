package com.telme.member.config;

import static com.telme.member.entity.SocialAccount.Provider.KAKAO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.telme.member.service.SocialAuthorizationFailureHandler;
import com.telme.member.service.SocialLinkRequest;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;
import java.time.Instant;
import com.telme.global.common.exception.GeneralException;
import com.telme.member.service.SocialLinkRequestStore;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

class SocialAuthorizationRequestResolverTest {

    private final SocialLinkRequestStore store = new SocialLinkRequestStore(Clock.systemUTC());
    private final ClientRegistration registration = ClientRegistration.withRegistrationId("kakao")
            .clientId("test-client")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
            .authorizationUri("https://example.com/oauth/authorize")
            .tokenUri("https://example.com/oauth/token")
            .build();
    private final ClientRegistration googleRegistration = ClientRegistration.withRegistrationId("google")
            .clientId("test-google-client")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
            .authorizationUri("https://example.com/google/authorize")
            .tokenUri("https://example.com/google/token")
            .build();
    private final SocialAuthorizationRequestResolver resolver = new SocialAuthorizationRequestResolver(
            new DefaultOAuth2AuthorizationRequestResolver(
                    new InMemoryClientRegistrationRepository(registration, googleRegistration),
                    "/oauth2/authorization"), store);

    @Test
    void 연결_중단_후_일반_로그인은_이전_연결을_사용하지_않는다() {
        MockHttpServletRequest linkStart = authorizationRequest(new MockHttpSession());
        linkStart.setParameter("link_token", store.issue(linkStart, 30L, KAKAO));
        resolver.resolve(linkStart);

        MockHttpServletRequest loginStart = authorizationRequest((MockHttpSession) linkStart.getSession());
        OAuth2AuthorizationRequest login = resolver.resolve(loginStart);

        loginStart.setParameter("state", login.getState());
        assertThat(store.consume(loginStart)).isNull();
    }

    @Test
    void 연결_인가_시작_전_중단해도_일반_로그인은_연결하지_않는다() {
        MockHttpServletRequest request = authorizationRequest(new MockHttpSession());
        store.issue(request, 30L, KAKAO);

        OAuth2AuthorizationRequest login = resolver.resolve(request);

        request.setParameter("state", login.getState());
        assertThat(store.consume(request)).isNull();
    }

    @Test
    void 연결_요청은_생성된_state의_콜백에서만_소비된다() {
        MockHttpServletRequest request = authorizationRequest(new MockHttpSession());
        String token = store.issue(request, 30L, KAKAO);
        request.setParameter("link_token", token);

        OAuth2AuthorizationRequest authorization = resolver.resolve(request);

        assertThat(authorization.getAuthorizationRequestUri()).doesNotContain(token, "link_token");
        request.setParameter("state", authorization.getState());
        assertThat(store.consume(request).targetUserId()).isEqualTo(30L);
        assertThat(store.consume(request)).isNull();
    }

    @Test
    void 다른_state의_콜백은_연결을_거부한다() {
        MockHttpServletRequest request = authorizationRequest(new MockHttpSession());
        request.setParameter("link_token", store.issue(request, 30L, KAKAO));
        resolver.resolve(request);
        request.setParameter("state", "different-state");

        assertThatThrownBy(() -> store.consume(request)).isInstanceOf(GeneralException.class);
    }

    @Test
    void 연결_시작_토큰은_재사용할_수_없다() {
        MockHttpServletRequest request = authorizationRequest(new MockHttpSession());
        request.setParameter("link_token", store.issue(request, 30L, KAKAO));
        OAuth2AuthorizationRequest authorization = resolver.resolve(request);

        assertThatThrownBy(() -> resolver.resolve(request)).isInstanceOf(OAuth2AuthenticationException.class);
        request.setParameter("state", authorization.getState());
        assertThat(store.consume(request).targetUserId()).isEqualTo(30L);
    }

    @Test
    void 카카오_연결_토큰으로_구글_인가를_시작하면_거부한다() {
        MockHttpServletRequest request = authorizationRequest(new MockHttpSession(), "google");
        request.setParameter("link_token", store.issue(request, 30L, KAKAO));

        assertThatThrownBy(() -> resolver.resolve(request)).isInstanceOf(OAuth2AuthenticationException.class);
    }

    @Test
    void 위조된_연결_토큰은_거부한다() {
        MockHttpServletRequest request = authorizationRequest(new MockHttpSession());
        store.issue(request, 30L, KAKAO);
        request.setParameter("link_token", "invalid-token");

        assertThatThrownBy(() -> resolver.resolve(request)).isInstanceOf(OAuth2AuthenticationException.class);
    }

    @Test
    void 재사용_토큰은_500_대신_실패_화면으로_이동하고_현재_연결은_보존한다() throws Exception {
        MockHttpServletRequest request = authorizationRequest(new MockHttpSession());
        request.setParameter("link_token", store.issue(request, 30L, KAKAO));
        OAuth2AuthorizationRequest authorization = resolver.resolve(request);
        var filter = new OAuth2AuthorizationRequestRedirectFilter(resolver);
        filter.setAuthenticationFailureHandler(new SocialAuthorizationFailureHandler(
                new Oauth2Properties("http://localhost:3000")));
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost:3000/oauth/callback?success=false&reason=MEMBER400-0");
        request.setParameter("state", authorization.getState());
        assertThat(store.consume(request).targetUserId()).isEqualTo(30L);
    }

    @Test
    void 만료된_토큰은_실패_화면으로_이동한다() throws Exception {
        MockHttpServletRequest request = authorizationRequest(new MockHttpSession());
        request.setParameter("link_token", "expired-token");
        request.getSession().setAttribute(SocialLinkRequestStore.SESSION_ATTRIBUTE,
                new SocialLinkRequest(30L, KAKAO,
                        Instant.now().minusSeconds(240), "expired-token", null));
        var filter = new OAuth2AuthorizationRequestRedirectFilter(resolver);
        filter.setAuthenticationFailureHandler(new SocialAuthorizationFailureHandler(
                new Oauth2Properties("http://localhost:3000")));
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost:3000/oauth/callback?success=false&reason=MEMBER400-0");
    }

    private MockHttpServletRequest authorizationRequest(MockHttpSession session) {
        return authorizationRequest(session, "kakao");
    }

    private MockHttpServletRequest authorizationRequest(MockHttpSession session, String registrationId) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorization/" + registrationId);
        request.setServletPath("/oauth2/authorization/" + registrationId);
        request.setSession(session);
        return request;
    }
}
