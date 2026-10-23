package com.telme.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.sentry.Breadcrumb;
import io.sentry.Hint;
import io.sentry.SentryEvent;
import io.sentry.protocol.Message;
import io.sentry.protocol.Request;
import io.sentry.protocol.SentryException;
import java.util.List;
import java.util.Map;
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

    @Test
    @DisplayName("요청 헤더는 User-Agent만 남기고 CloudFront가 넘긴 IP·위치 헤더를 지운다")
    void 요청_헤더는_User_Agent만_남긴다() {
        Request request = new Request();
        request.setHeaders(Map.of(
                "user-agent", "Mozilla/5.0",
                "CloudFront-Viewer-Address", "203.0.113.10:51234",
                "CloudFront-Viewer-Latitude", "37.56650",
                "CloudFront-Viewer-Longitude", "126.97800",
                "CloudFront-Viewer-Postal-Code", "04524",
                "CloudFront-Viewer-City", "Seoul",
                "Accept", "application/json"));
        SentryEvent event = new SentryEvent();
        event.setRequest(request);

        SentryEvent sent = config.sentryBeforeSend().execute(event, new Hint());

        assertThat(sent.getRequest().getHeaders()).containsExactly(Map.entry("user-agent", "Mozilla/5.0"));
    }

    @Test
    @DisplayName("User-Agent가 없으면 요청 헤더를 비운다")
    void User_Agent가_없으면_헤더를_비운다() {
        Request request = new Request();
        request.setHeaders(Map.of("CloudFront-Viewer-Address", "203.0.113.10:51234"));
        SentryEvent event = new SentryEvent();
        event.setRequest(request);

        assertThat(config.sentryBeforeSend().execute(event, new Hint()).getRequest().getHeaders()).isEmpty();
    }

    @Test
    @DisplayName("DB 오류의 Detail에 담긴 입력값(이메일 등)을 예외 값과 로그 메시지에서 지운다")
    void DB_오류의_입력값을_지운다() {
        String dbMessage = "could not execute statement [ERROR: duplicate key value violates unique constraint "
                + "\"uk_users_email\"\n  Detail: Key (email)=(tester@example.com) already exists.] [insert into users]";
        SentryException exception = new SentryException();
        exception.setValue(dbMessage);
        Message message = new Message();
        message.setMessage("[Unhandled Exception] {}");
        message.setFormatted("[Unhandled Exception] " + dbMessage);
        message.setParams(List.of(dbMessage));
        SentryEvent event = new SentryEvent();
        event.setExceptions(List.of(exception));
        event.setMessage(message);

        SentryEvent sent = config.sentryBeforeSend().execute(event, new Hint());

        String expected = "could not execute statement [ERROR: duplicate key value violates unique constraint "
                + "\"uk_users_email\"\n  " + SentryConfig.DETAIL_FILTERED;
        assertThat(sent.getExceptions().get(0).getValue()).isEqualTo(expected);
        assertThat(sent.getMessage().getFormatted()).isEqualTo("[Unhandled Exception] " + expected);
        assertThat(sent.getMessage().getParams()).containsExactly(expected);
        assertThat(sent.getMessage().getMessage()).isEqualTo("[Unhandled Exception] {}");
    }

    @Test
    @DisplayName("Detail이 없는 오류 메시지는 그대로 둔다")
    void Detail이_없으면_그대로_둔다() {
        SentryException exception = new SentryException();
        exception.setValue("예상 못 한 오류");
        SentryEvent event = new SentryEvent();
        event.setExceptions(List.of(exception));

        assertThat(config.sentryBeforeSend().execute(event, new Hint()).getExceptions().get(0).getValue())
                .isEqualTo("예상 못 한 오류");
    }

    @Test
    @DisplayName("답변 검사 로그의 흐름 기록은 사유만 남기고 모델이 만든 문장을 지운다")
    void 답변_검사_로그는_사유만_남긴다() {
        Breadcrumb breadcrumb = new Breadcrumb();
        breadcrumb.setCategory(SentryConfig.ANSWER_GUARD_LOGGER);
        breadcrumb.setMessage("[AnswerGuard] 근거에 없는 정책 단정으로 문장 제거: 위약금 | 해지하면 위약금이 없습니다.");

        Breadcrumb kept = config.sentryBeforeBreadcrumb().execute(breadcrumb, new Hint());

        assertThat(kept.getMessage()).isEqualTo("[AnswerGuard] 근거에 없는 정책 단정으로 문장 제거");
    }

    @Test
    @DisplayName("다른 로그의 흐름 기록은 문장을 그대로 두고 DB 입력값만 지운다")
    void 다른_로그는_DB_입력값만_지운다() {
        Breadcrumb plain = new Breadcrumb();
        plain.setCategory("com.telme.chat.service.ChatContextBuilder");
        plain.setMessage("Context에서 유효하지 않은 채팅 메시지 제외: messageId=3, sequenceNo=2, reason=EMPTY");
        Breadcrumb db = new Breadcrumb();
        db.setCategory("org.hibernate.engine.jdbc.spi.SqlExceptionHelper");
        db.setMessage("ERROR: duplicate key value\n  Detail: Key (email)=(tester@example.com) already exists.");

        assertThat(config.sentryBeforeBreadcrumb().execute(plain, new Hint()).getMessage())
                .isEqualTo("Context에서 유효하지 않은 채팅 메시지 제외: messageId=3, sequenceNo=2, reason=EMPTY");
        assertThat(config.sentryBeforeBreadcrumb().execute(db, new Hint()).getMessage())
                .isEqualTo("ERROR: duplicate key value\n  " + SentryConfig.DETAIL_FILTERED);
    }
}
