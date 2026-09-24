package com.telme.global.config;

import com.telme.member.filter.GuestIdentityFilter;
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

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            GuestIdentityFilter guestIdentityFilter,
            SecurityContextRepository securityContextRepository
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
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(SWAGGER_WHITELIST).permitAll()
                        .requestMatchers(PUBLIC_WHITELIST).permitAll()
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
