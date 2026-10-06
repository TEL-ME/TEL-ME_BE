package com.telme.store.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties({StoreSearchProperties.class, KakaoLocalProperties.class})
public class StoreConfig {

    private static final String KAKAO_AUTH_SCHEME = "KakaoAK ";

    @Bean
    public RestClient kakaoLocalRestClient(RestClient.Builder builder, KakaoLocalProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());

        return builder
                .baseUrl(properties.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, KAKAO_AUTH_SCHEME + properties.restApiKey())
                .requestFactory(requestFactory)
                .build();
    }
}
