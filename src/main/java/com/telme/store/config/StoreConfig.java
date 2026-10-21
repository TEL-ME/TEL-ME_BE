package com.telme.store.config;

import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties({StoreSearchProperties.class, KakaoLocalProperties.class,
        KakaoRouteProperties.class, StoreRouteCacheProperties.class, StoreRouteProperties.class})
public class StoreConfig {

    private static final String KAKAO_AUTH_SCHEME = "KakaoAK ";

    @Bean
    public RestClient kakaoLocalRestClient(RestClient.Builder builder, KakaoLocalProperties properties) {
        return kakaoRestClient(builder, properties.baseUrl(), properties.restApiKey(),
                properties.connectTimeout(), properties.readTimeout());
    }

    @Bean
    public RestClient kakaoRouteRestClient(RestClient.Builder builder, KakaoRouteProperties properties) {
        return kakaoRestClient(builder, properties.mapBaseUrl(), properties.restApiKey(),
                properties.connectTimeout(), properties.readTimeout());
    }

    @Bean
    public RestClient kakaoNaviRestClient(RestClient.Builder builder, KakaoRouteProperties properties) {
        return kakaoRestClient(builder, properties.naviBaseUrl(), properties.restApiKey(),
                properties.connectTimeout(), properties.readTimeout());
    }

    private RestClient kakaoRestClient(RestClient.Builder builder, String baseUrl, String restApiKey,
                                       Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);

        return builder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, KAKAO_AUTH_SCHEME + restApiKey)
                .requestFactory(requestFactory)
                .build();
    }
}
