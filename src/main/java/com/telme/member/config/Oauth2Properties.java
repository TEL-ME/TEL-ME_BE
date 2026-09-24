package com.telme.member.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "member.oauth2")
public record Oauth2Properties(String frontendUri) {

    public Oauth2Properties {
        if (frontendUri == null || frontendUri.isBlank()) {
            throw new IllegalArgumentException("member.oauth2.frontend-uri 설정이 필요합니다.");
        }
    }
}
