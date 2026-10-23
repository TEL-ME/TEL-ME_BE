package com.telme.global.config;

import com.telme.rag.service.AnswerGuard;
import io.sentry.Breadcrumb;
import io.sentry.SentryEvent;
import io.sentry.SentryOptions;
import io.sentry.protocol.Message;
import io.sentry.protocol.Request;
import io.sentry.protocol.SentryException;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Sentry로 보내기 전에 사용자 정보가 담길 수 있는 값을 지운다. send-default-pii=false가 막지 못하는 것만 여기서 다룬다.
// 1) 쿼리스트링: 매장 근처 검색(/api/v1/stores/nearby?latitude=…&longitude=…) 중 오류가 나면 사용자 좌표가 남는다
// 2) 요청 헤더: CloudFront가 넘기는 CloudFront-Viewer-Address(IP)·Latitude·Longitude·Postal-Code·City 같은 위치 헤더는
//    SDK가 거르는 목록(인증·쿠키·X-Forwarded-For 등)에 없어 그대로 간다. 원인 분석에 쓰는 User-Agent만 남긴다
// 3) DB 오류 메시지의 Detail: PostgreSQL은 중복 오류에 "Detail: Key (email)=(…) already exists."처럼 입력값을 붙인다
// 4) 답변 검사(AnswerGuard)가 WARN으로 남기는 모델 생성 문장: 오류 직전 흐름 기록에서 사유만 남긴다
@Configuration
public class SentryConfig {

    static final String HTTP_QUERY = "http.query";
    static final String HTTP_FRAGMENT = "http.fragment";
    static final String URL = "url";
    static final String USER_AGENT = "User-Agent";
    static final String DETAIL_FILTERED = "Detail: [Filtered]";
    static final String ANSWER_GUARD_LOGGER = AnswerGuard.class.getName();

    // "Detail:"부터 끝까지 지운다. 값에 괄호·줄바꿈이 들어갈 수 있어 값의 끝을 찾지 않는다
    private static final Pattern DB_DETAIL = Pattern.compile("Detail:.*", Pattern.DOTALL);

    // 우리 서버로 들어온 요청과 오류 내용
    @Bean
    public SentryOptions.BeforeSendCallback sentryBeforeSend() {
        return (event, hint) -> {
            Request request = event.getRequest();
            if (request != null) {
                request.setQueryString(null);
                request.setHeaders(keepUserAgent(request.getHeaders()));
            }
            removeDbDetail(event);
            return event;
        };
    }

    // 오류 직전 흐름에 남는 외부 HTTP 호출(카카오 주소·좌표 검색 등)의 쿼리스트링과 로그 문장
    @Bean
    public SentryOptions.BeforeBreadcrumbCallback sentryBeforeBreadcrumb() {
        return (breadcrumb, hint) -> {
            breadcrumb.removeData(HTTP_QUERY);
            breadcrumb.removeData(HTTP_FRAGMENT);
            if (breadcrumb.getData(URL) instanceof String url && url.indexOf('?') >= 0) {
                breadcrumb.setData(URL, url.substring(0, url.indexOf('?')));
            }
            breadcrumb.setMessage(ANSWER_GUARD_LOGGER.equals(breadcrumb.getCategory())
                    ? keepAnswerGuardReason(breadcrumb.getMessage())
                    : removeDbDetail(breadcrumb.getMessage()));
            return breadcrumb;
        };
    }

    private Map<String, String> keepUserAgent(Map<String, String> headers) {
        if (headers == null) {
            return null;
        }
        return headers.entrySet().stream()
                .filter(header -> USER_AGENT.equalsIgnoreCase(header.getKey()) && header.getValue() != null)
                .findFirst()
                .map(header -> Map.of(header.getKey(), header.getValue()))
                .orElse(Map.of());
    }

    // 처리하지 않은 예외는 예외 값에, ERROR 로그는 메시지와 그 인자에 Detail이 담길 수 있다
    private void removeDbDetail(SentryEvent event) {
        List<SentryException> exceptions = event.getExceptions();
        if (exceptions != null) {
            exceptions.forEach(exception -> exception.setValue(removeDbDetail(exception.getValue())));
        }
        Message message = event.getMessage();
        if (message != null) {
            message.setMessage(removeDbDetail(message.getMessage()));
            message.setFormatted(removeDbDetail(message.getFormatted()));
            if (message.getParams() != null) {
                message.setParams(message.getParams().stream().map(this::removeDbDetail).toList());
            }
        }
    }

    private String removeDbDetail(String text) {
        return text == null ? null : DB_DETAIL.matcher(text).replaceFirst(DETAIL_FILTERED);
    }

    // "[AnswerGuard] 근거에 없는 금액 발견: {문장}"처럼 사유 뒤에 문장이 붙으므로 첫 ": " 앞까지만 남긴다
    private String keepAnswerGuardReason(String message) {
        if (message == null) {
            return null;
        }
        int separator = message.indexOf(": ");
        return separator >= 0 ? message.substring(0, separator) : "[AnswerGuard]";
    }
}
