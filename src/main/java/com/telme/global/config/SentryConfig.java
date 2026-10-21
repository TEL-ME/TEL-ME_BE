package com.telme.global.config;

import io.sentry.SentryOptions;
import io.sentry.protocol.Request;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Sentry로 보내기 전에 주소의 쿼리스트링을 지운다. send-default-pii=false는 헤더·쿠키·IP만 막고 쿼리스트링은 그대로 보내서,
// 매장 근처 검색(/api/v1/stores/nearby?latitude=…&longitude=…) 중 오류가 나면 사용자 좌표가 오류 기록에 남는다
@Configuration
public class SentryConfig {

    static final String HTTP_QUERY = "http.query";
    static final String HTTP_FRAGMENT = "http.fragment";
    static final String URL = "url";

    // 우리 서버로 들어온 요청의 쿼리스트링
    @Bean
    public SentryOptions.BeforeSendCallback sentryBeforeSend() {
        return (event, hint) -> {
            Request request = event.getRequest();
            if (request != null) {
                request.setQueryString(null);
            }
            return event;
        };
    }

    // 오류 직전 흐름에 남는 외부 HTTP 호출(카카오 주소·좌표 검색 등)의 쿼리스트링
    @Bean
    public SentryOptions.BeforeBreadcrumbCallback sentryBeforeBreadcrumb() {
        return (breadcrumb, hint) -> {
            breadcrumb.removeData(HTTP_QUERY);
            breadcrumb.removeData(HTTP_FRAGMENT);
            if (breadcrumb.getData(URL) instanceof String url && url.indexOf('?') >= 0) {
                breadcrumb.setData(URL, url.substring(0, url.indexOf('?')));
            }
            return breadcrumb;
        };
    }
}
