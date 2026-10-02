package com.telme.store.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// 카카오 로컬 API(주소·키워드 검색). 채팅 응답을 기다리게 하므로 타임아웃을 짧게 둔다
@ConfigurationProperties(prefix = "kakao.local")
public record KakaoLocalProperties(
        @DefaultValue("https://dapi.kakao.com") String baseUrl,
        String restApiKey,
        @DefaultValue("2s") Duration connectTimeout,
        @DefaultValue("3s") Duration readTimeout) {

    public KakaoLocalProperties {
        if (restApiKey == null || restApiKey.isBlank()) {
            throw new IllegalArgumentException("카카오 로컬 API 키(kakao.local.rest-api-key)가 필요합니다.");
        }
    }
}
