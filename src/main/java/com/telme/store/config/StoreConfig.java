package com.telme.store.config;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties({StoreSearchProperties.class, KakaoLocalProperties.class,
        KakaoRouteProperties.class, StoreRouteProperties.class, StoreRouteUsageProperties.class,
        StoreOriginWalkProperties.class})
public class StoreConfig {

    private static final String KAKAO_AUTH_SCHEME = "KakaoAK ";

    @Bean
    public RestClient kakaoLocalRestClient(RestClient.Builder builder, KakaoLocalProperties properties) {
        return kakaoRestClient(builder, properties.baseUrl(), properties.restApiKey(),
                properties.connectTimeout(), properties.readTimeout(), true);
    }

    @Bean
    public RestClient kakaoRouteRestClient(RestClient.Builder builder, KakaoRouteProperties properties) {
        return kakaoRestClient(builder, properties.mapBaseUrl(), properties.restApiKey(),
                properties.connectTimeout(), properties.readTimeout(), false);
    }

    @Bean
    public RestClient kakaoNaviRestClient(RestClient.Builder builder, KakaoRouteProperties properties) {
        return kakaoRestClient(builder, properties.naviBaseUrl(), properties.restApiKey(),
                properties.connectTimeout(), properties.readTimeout(), false);
    }

    private RestClient kakaoRestClient(RestClient.Builder builder, String baseUrl, String restApiKey,
                                       Duration connectTimeout, Duration readTimeout, boolean followRedirects) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String method) throws IOException {
                super.prepareConnection(connection, method);
                // 경로 호출 1회 예약이 자동 리다이렉트의 추가 외부 요청으로 늘어나지 않도록 한다.
                connection.setInstanceFollowRedirects(followRedirects);
            }
        };
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);

        return builder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, KAKAO_AUTH_SCHEME + restApiKey)
                .requestFactory(requestFactory)
                .build();
    }
}
