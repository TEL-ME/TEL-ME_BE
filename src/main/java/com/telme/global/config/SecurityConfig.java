package com.telme.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.member.filter.GuestIdentityFilter;
import com.telme.member.config.KakaoAuthorizationRequestResolver;
import com.telme.member.service.KakaoLinkRequestStore;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import com.telme.member.service.KakaoLoginFailureHandler;
import com.telme.member.service.KakaoLoginSuccessHandler;
import com.telme.member.service.KakaoOAuth2UserService;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String[] SWAGGER_WHITELIST = {
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/swagger-resources/**",
            "/webjars/**"
    };

    private static final String[] PUBLIC_WHITELIST = {
            "/actuator/health",
            "/actuator/info"
    };

    private static final String[] OAUTH2_WHITELIST = {
            "/oauth2/authorization/**",
            "/login/oauth2/code/**"
    };

    // /api/v1/auth/** 는 대부분 permitAll이지만, 이 하위 경로는 로그인된 회원 자신만 호출할 수 있어야 한다 —
    // authorizeHttpRequests는 먼저 매칭된 규칙이 우선이라 더 넓은 permitAll보다 앞에 선언한다
    private static final String[] AUTHENTICATED_AUTH_WHITELIST = {
            "/api/auth/login-methods/**",
            "/api/v1/auth/login-methods/**",
            "/api/auth/kakao/link-start",
            "/api/v1/auth/kakao/link-start"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            GuestIdentityFilter guestIdentityFilter,
            SecurityContextRepository securityContextRepository,
            KakaoOAuth2UserService kakaoOAuth2UserService,
            KakaoLoginSuccessHandler kakaoLoginSuccessHandler,
            KakaoLoginFailureHandler kakaoLoginFailureHandler,
            RestAuthenticationEntryPoint restAuthenticationEntryPoint,
            ClientRegistrationRepository clientRegistrationRepository,
            KakaoLinkRequestStore kakaoLinkRequestStore
    ) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                // 로그인 서비스가 이 저장소로 SecurityContext를 명시적으로 저장 (SecurityContextHolderFilter는 자동 저장 안 함)
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .addFilterAfter(guestIdentityFilter, SecurityContextHolderFilter.class)
                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(
                                new KakaoAuthorizationRequestResolver(
                                        new DefaultOAuth2AuthorizationRequestResolver(
                                                clientRegistrationRepository, "/oauth2/authorization"),
                                        kakaoLinkRequestStore)))
                        .userInfoEndpoint(userInfo -> userInfo.userService(kakaoOAuth2UserService))
                        .successHandler(kakaoLoginSuccessHandler)
                        .failureHandler(kakaoLoginFailureHandler)
                )
                // oauth2Login 기본값(카카오 리다이렉트) 대신 /api/**는 다른 API 오류와 같은 형식(401 JSON)으로 응답한다
                .exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
                        restAuthenticationEntryPoint,
                        PathPatternRequestMatcher.withDefaults().matcher("/api/**")))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(SWAGGER_WHITELIST).permitAll()
                        .requestMatchers(PUBLIC_WHITELIST).permitAll()
                        .requestMatchers(OAUTH2_WHITELIST).permitAll()
                        .requestMatchers(AUTHENTICATED_AUTH_WHITELIST).authenticated()
                        .requestMatchers("/api/auth/**", "/api/v1/auth/**").permitAll()
                        .requestMatchers("/api/chat/**", "/api/v1/chat/**").permitAll()
                        .requestMatchers("/api/stores/**", "/api/v1/stores/**").permitAll()
                        .requestMatchers("/api/faq/**", "/api/v1/faq/**").permitAll()
                        .requestMatchers("/api/intent-routes/**", "/api/v1/intent-routes/**").permitAll()
                        .requestMatchers("/api/admin/**", "/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    // @Component 대신 여기 @Bean으로 둬야 슬라이스 테스트(@Import(SecurityConfig.class))에서도 잡힌다
    @Bean
    public RestAuthenticationEntryPoint restAuthenticationEntryPoint(ObjectMapper objectMapper) {
        return new RestAuthenticationEntryPoint(objectMapper);
    }

    // @Component 자동 등록으로 인한 서블릿 중복 실행을 막기 위해 비활성화
    @Bean
    public FilterRegistrationBean<GuestIdentityFilter> guestIdentityFilterRegistration(
            GuestIdentityFilter guestIdentityFilter
    ) {
        FilterRegistrationBean<GuestIdentityFilter> registration =
                new FilterRegistrationBean<>(guestIdentityFilter);
        registration.setEnabled(false);
        return registration;
    }
}
