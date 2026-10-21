package com.telme.member.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientProperties;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientPropertiesMapper;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

// 운영은 CloudFront(HTTPS) → ALB(HTTP) → 앱이라 요청 헤더로 만든 주소가 http://<ALB 주소>가 된다.
// application.yml + application-prod.yml을 실제로 읽어, ALB가 넘겨준 요청에서도 서비스 주소로 redirect_uri가 나가는지 확인한다
class ProdOauth2RedirectUriTest {

    private static final String SITE_URL = "https://www.telme.site";
    private static final String ALB_HOST = "telme-alb-123.ap-northeast-2.elb.amazonaws.com";

    @ParameterizedTest
    @ValueSource(strings = {"kakao", "google"})
    @DisplayName("prod는 ALB가 넘긴 http 요청이어도 redirect_uri를 FRONTEND_URL 기준 https로 보낸다")
    void prod는_서비스_주소로_redirect_uri를_만든다(String registrationId) throws IOException {
        OAuth2AuthorizationRequest request = resolve(environment(true, SITE_URL), registrationId);

        assertThat(request.getRedirectUri())
                .isEqualTo(SITE_URL + "/login/oauth2/code/" + registrationId);
    }

    @Test
    @DisplayName("prod가 아니면 지금처럼 요청 주소({baseUrl})로 만든다")
    void prod가_아니면_요청_주소로_만든다() throws IOException {
        OAuth2AuthorizationRequest request = resolve(environment(false, SITE_URL), "kakao");

        assertThat(request.getRedirectUri()).isEqualTo("http://" + ALB_HOST + "/login/oauth2/code/kakao");
    }

    @Test
    @DisplayName("prod에서 FRONTEND_URL이 없으면 깨진 주소(${FRONTEND_URL}) 대신 요청 주소로 만든다")
    void prod에서_FRONTEND_URL이_없으면_요청_주소로_만든다() throws IOException {
        OAuth2AuthorizationRequest request = resolve(environment(true, null), "kakao");

        assertThat(request.getRedirectUri()).isEqualTo("http://" + ALB_HOST + "/login/oauth2/code/kakao");
    }

    private static StandardEnvironment environment(boolean prod, String frontendUrl) throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        MutablePropertySources sources = environment.getPropertySources();
        // 시스템 환경 변수에 FRONTEND_URL이 있어도 테스트 값만 보도록 지운다
        sources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        if (frontendUrl != null) {
            sources.addLast(new MapPropertySource("test-env", Map.of("FRONTEND_URL", frontendUrl)));
        }
        if (prod) {
            load("application-prod.yml").forEach(sources::addLast);
        }
        load("application.yml").forEach(sources::addLast);
        return environment;
    }

    private static Iterable<PropertySource<?>> load(String name) throws IOException {
        return new YamlPropertySourceLoader().load(name, new ClassPathResource(name));
    }

    private static OAuth2ClientProperties bind(StandardEnvironment environment) {
        return new Binder(ConfigurationPropertySources.get(environment),
                new PropertySourcesPlaceholdersResolver(environment))
                .bind("spring.security.oauth2.client", OAuth2ClientProperties.class)
                .get();
    }

    // ALB가 앱에 넘기는 요청 모양: Host는 ALB 주소, 80번 포트, http
    private static OAuth2AuthorizationRequest resolve(StandardEnvironment environment, String registrationId) {
        OAuth2ClientProperties properties = bind(environment);
        var repository = new InMemoryClientRegistrationRepository(
                new OAuth2ClientPropertiesMapper(properties).asClientRegistrations());
        var resolver = new DefaultOAuth2AuthorizationRequestResolver(repository, "/oauth2/authorization");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorization/" + registrationId);
        request.setScheme("http");
        request.setServerName(ALB_HOST);
        request.setServerPort(80);
        request.setServletPath("/oauth2/authorization/" + registrationId);
        return resolver.resolve(request);
    }
}
