package com.telme.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    private static final String SECURITY_SCHEME_NAME = "SessionAuth";

    @Bean
    public OpenAPI openAPI() {
        SecurityScheme sessionAuthScheme = new SecurityScheme()
                .name("JSESSIONID")
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.COOKIE)
                .description("세션 쿠키 (JSESSIONID)");

        SecurityRequirement securityRequirement = new SecurityRequirement()
                .addList(SECURITY_SCHEME_NAME);

        Info info = new Info()
                .title("TEL-ME API 명세서")
                .description("RAG 기반 AI 통신 상담 및 위치 기반 매장 안내 서비스 백엔드 API")
                .version("v1.0.0")
                .contact(new Contact()
                        .name("TEL-ME 백엔드 팀")
                        .url("https://github.com/TEL-ME/TEL-ME_BE"));

        Server localServer = new Server()
                .url("http://localhost:8080")
                .description("Local Development Server");

        return new OpenAPI()
                .info(info)
                .servers(List.of(localServer))
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME_NAME, sessionAuthScheme))
                .addSecurityItem(securityRequirement);
    }
}