package com.telme.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.sentry.Breadcrumb;
import io.sentry.Hint;
import io.sentry.SentryEvent;
import io.sentry.protocol.Request;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SentryConfigTest {

    private final SentryConfig config = new SentryConfig();

    @Test
    @DisplayName("오류 기록의 요청 쿼리스트링(위치 좌표)을 지우고 주소는 남긴다")
    void 요청_쿼리스트링을_지운다() {
        Request request = new Request();
        request.setUrl("http://localhost/api/v1/stores/nearby");
        request.setQueryString("latitude=37.5665&longitude=126.9780");
        SentryEvent event = new SentryEvent();
        event.setRequest(request);

        SentryEvent sent = config.sentryBeforeSend().execute(event, new Hint());

        assertThat(sent).isSameAs(event);
        assertThat(sent.getRequest().getQueryString()).isNull();
        assertThat(sent.getRequest().getUrl()).isEqualTo("http://localhost/api/v1/stores/nearby");
    }

    @Test
    @DisplayName("요청 정보가 없는 오류(채팅 비동기 처리 등)는 그대로 보낸다")
    void 요청_정보가_없어도_보낸다() {
        SentryEvent event = new SentryEvent();

        assertThat(config.sentryBeforeSend().execute(event, new Hint())).isSameAs(event);
    }

    @Test
    @DisplayName("오류 직전 흐름의 외부 HTTP 호출에서 쿼리스트링을 지운다")
    void 외부_호출_쿼리스트링을_지운다() {
        Breadcrumb breadcrumb = Breadcrumb.http(
                "https://dapi.kakao.com/v2/local/geo/coord2address.json?x=126.9780&y=37.5665#top", "GET");

        Breadcrumb kept = config.sentryBeforeBreadcrumb().execute(breadcrumb, new Hint());

        assertThat(kept).isSameAs(breadcrumb);
        assertThat(kept.getData(SentryConfig.HTTP_QUERY)).isNull();
        assertThat(kept.getData(SentryConfig.HTTP_FRAGMENT)).isNull();
        assertThat(kept.getData(SentryConfig.URL)).isEqualTo("https://dapi.kakao.com/v2/local/geo/coord2address.json");
    }

    @Test
    @DisplayName("주소에 쿼리스트링이 남아 있는 흐름 기록도 주소만 남긴다")
    void 주소에_남은_쿼리스트링을_지운다() {
        Breadcrumb breadcrumb = new Breadcrumb();
        breadcrumb.setData(SentryConfig.URL, "https://dapi.kakao.com/v2/local/search/address.json?query=서울");

        Breadcrumb kept = config.sentryBeforeBreadcrumb().execute(breadcrumb, new Hint());

        assertThat(kept.getData(SentryConfig.URL)).isEqualTo("https://dapi.kakao.com/v2/local/search/address.json");
    }
}
