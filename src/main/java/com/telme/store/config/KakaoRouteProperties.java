package com.telme.store.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// 카카오맵 경로 API(대중교통·도보·자전거)와 카카오모빌리티 자동차 길찾기. 둘 다 같은 앱의 REST API 키를 쓴다.
// 로컬 검색보다 계산이 오래 걸려 읽기 타임아웃을 더 길게 둔다
@ConfigurationProperties(prefix = "kakao.route")
public record KakaoRouteProperties(
        @DefaultValue("https://dapi.kakao.com") String mapBaseUrl,
        @DefaultValue("https://apis-navi.kakaomobility.com") String naviBaseUrl,
        String restApiKey,
        @DefaultValue("2s") Duration connectTimeout,
        @DefaultValue("5s") Duration readTimeout,
        @DefaultValue("false") boolean transitOriginWalkEnabled) {

    public KakaoRouteProperties {
        if (restApiKey == null || restApiKey.isBlank()) {
            throw new IllegalArgumentException("카카오 경로 API 키(kakao.route.rest-api-key)가 필요합니다.");
        }
    }
}
