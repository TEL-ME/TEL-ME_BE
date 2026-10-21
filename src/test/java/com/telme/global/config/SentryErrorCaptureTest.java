package com.telme.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.telme.faq.controller.FaqController;
import com.telme.faq.exception.FaqErrorCode;
import com.telme.faq.service.FaqQueryService;
import com.telme.global.common.exception.GeneralException;
import com.telme.member.repository.UserRepository;
import com.telme.member.service.GoogleOidcUserService;
import com.telme.member.service.GuestIdentityService;
import com.telme.member.service.KakaoOAuth2UserService;
import com.telme.member.service.MemberStatusChecker;
import com.telme.member.service.SocialAuthorizationFailureHandler;
import com.telme.member.service.SocialLinkRequestStore;
import com.telme.member.service.SocialLoginFailureHandler;
import com.telme.member.service.SocialLoginSuccessHandler;
import io.sentry.Hint;
import io.sentry.ITransportFactory;
import io.sentry.Sentry;
import io.sentry.SentryEnvelope;
import io.sentry.SentryEnvelopeItem;
import io.sentry.SentryEvent;
import io.sentry.SentryItemType;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.spring.boot.jakarta.SentryAutoConfiguration;
import io.sentry.spring.boot.jakarta.SentryLogbackAppenderAutoConfiguration;
import io.sentry.transport.ITransport;
import io.sentry.transport.RateLimiter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

// application.yml의 sentry 설정과 SentryConfig로 실제 요청을 흘려 어떤 오류가 Sentry로 가는지 확인한다.
// 가짜 DSN을 넣고 전송은 기록만 하는 전송기로 바꿔 외부로 보내지 않는다.
// 테스트 설정은 Sentry 자동 설정을 빼 두므로(application.properties) 여기서만 제외를 비우고 켠다
@WebMvcTest(FaqController.class)
@Import({SecurityConfig.class, MemberStatusChecker.class, SentryConfig.class, SentryErrorCaptureTest.RecordingTransportConfig.class})
@ImportAutoConfiguration({SentryAutoConfiguration.class, SentryLogbackAppenderAutoConfiguration.class})
@TestPropertySource(properties = {"sentry.dsn=https://public@localhost/1", "spring.autoconfigure.exclude="})
class SentryErrorCaptureTest {

    private static final List<SentryEvent> EVENTS = new CopyOnWriteArrayList<>();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FaqQueryService faqQueryService;

    // SecurityConfig와 요청 필터가 요구하는 의존성 — 웹 슬라이스에는 없어 목으로 채운다
    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private GuestIdentityService guestIdentityService;

    @MockitoBean
    private KakaoOAuth2UserService kakaoOAuth2UserService;

    @MockitoBean
    private GoogleOidcUserService googleOidcUserService;

    @MockitoBean
    private SocialLoginSuccessHandler socialLoginSuccessHandler;

    @MockitoBean
    private SocialLoginFailureHandler socialLoginFailureHandler;

    @MockitoBean
    private SocialAuthorizationFailureHandler socialAuthorizationFailureHandler;

    @MockitoBean
    private SocialLinkRequestStore socialLinkRequestStore;

    @BeforeEach
    void setUp() {
        EVENTS.clear();
    }

    // 이 테스트가 켠 전역 Sentry를 꺼서 뒤에 도는 테스트의 ERROR 로그가 기록되지 않게 한다
    @AfterAll
    static void tearDown() {
        Sentry.close();
    }

    @Test
    @DisplayName("처리하지 않은 예외는 보내고, 요청 쿼리스트링(위치 좌표)은 지운다")
    void 처리하지_않은_예외는_쿼리스트링_없이_보낸다() throws Exception {
        when(faqQueryService.getFaqs(any())).thenThrow(new IllegalStateException("예상 못 한 오류"));

        mockMvc.perform(get("/api/v1/faqs?latitude=37.5665&longitude=126.9780"))
                .andExpect(status().isInternalServerError());

        assertThat(EVENTS).hasSize(1);
        SentryEvent event = EVENTS.get(0);
        assertThat(event.getExceptions()).anySatisfy(exception ->
                assertThat(exception.getType()).isEqualTo("IllegalStateException"));
        assertThat(event.getRequest()).isNotNull();
        assertThat(event.getRequest().getUrl()).endsWith("/api/v1/faqs");
        assertThat(event.getRequest().getQueryString()).isNull();
    }

    @Test
    @DisplayName("없는 경로는 500으로 응답하지만 요청 쪽 실수라 보내지 않는다")
    void 없는_경로는_보내지_않는다() throws Exception {
        mockMvc.perform(get("/api/v1/faqs/1/typo")).andExpect(status().isInternalServerError());

        assertThat(EVENTS).isEmpty();
    }

    @Test
    @DisplayName("허용 안 된 HTTP 메서드는 500으로 응답하지만 요청 쪽 실수라 보내지 않는다")
    void 허용_안_된_메서드는_보내지_않는다() throws Exception {
        mockMvc.perform(delete("/api/v1/faqs")).andExpect(status().isInternalServerError());

        assertThat(EVENTS).isEmpty();
    }

    @Test
    @DisplayName("4xx GeneralException은 WARN이라 보내지 않는다")
    void 사용자_요청_오류는_보내지_않는다() throws Exception {
        when(faqQueryService.getFaq(9L)).thenThrow(new GeneralException(FaqErrorCode.FAQ_NOT_FOUND));

        mockMvc.perform(get("/api/v1/faqs/9")).andExpect(status().isNotFound());

        assertThat(EVENTS).isEmpty();
    }

    @Test
    @DisplayName("사람이 고쳐야 하는 5xx 오류 코드는 ERROR로 올려 보낸다")
    void 알림_오류_코드는_보낸다() throws Exception {
        when(faqQueryService.getFaq(9L)).thenThrow(new GeneralException(FaqErrorCode.EMBEDDING_RESPONSE_INVALID));

        mockMvc.perform(get("/api/v1/faqs/9")).andExpect(status().isInternalServerError());

        assertThat(EVENTS).hasSize(1);
        SentryEvent event = EVENTS.get(0);
        assertThat(event.getLevel()).isEqualTo(SentryLevel.ERROR);
        assertThat(event.getMessage().getFormatted()).contains("FAQ500-0");
        assertThat(event.getExceptions()).anySatisfy(exception ->
                assertThat(exception.getType()).isEqualTo("GeneralException"));
    }

    @Test
    @DisplayName("일시 장애 성격의 5xx 오류 코드는 WARN으로 두어 보내지 않는다")
    void 일시_장애_오류_코드는_보내지_않는다() throws Exception {
        when(faqQueryService.getFaq(9L)).thenThrow(new GeneralException(FaqErrorCode.EMBEDDING_REQUEST_FAILED));

        mockMvc.perform(get("/api/v1/faqs/9")).andExpect(status().isServiceUnavailable());

        assertThat(EVENTS).isEmpty();
    }

    @Test
    @DisplayName("요청 쪽 실수 예외 3종이 수집 제외로 설정된다")
    void 요청_쪽_실수_예외를_제외한다() {
        assertThat(Sentry.getCurrentScopes().getOptions().getIgnoredExceptionsForType()).contains(
                NoResourceFoundException.class,
                HttpRequestMethodNotSupportedException.class,
                HttpMediaTypeNotSupportedException.class);
    }

    @TestConfiguration
    static class RecordingTransportConfig {

        @Bean
        ITransportFactory recordingTransportFactory() {
            return (options, requestDetails) -> new RecordingTransport(options);
        }
    }

    // 보낼 오류를 외부로 보내지 않고 EVENTS에 모은다
    private static final class RecordingTransport implements ITransport {

        private final SentryOptions options;
        private final RateLimiter rateLimiter;

        private RecordingTransport(SentryOptions options) {
            this.options = options;
            this.rateLimiter = new RateLimiter(options);
        }

        @Override
        public void send(SentryEnvelope envelope, Hint hint) {
            for (SentryEnvelopeItem item : envelope.getItems()) {
                if (item.getHeader().getType() == SentryItemType.Event) {
                    try {
                        EVENTS.add(item.getEvent(options.getSerializer()));
                    } catch (Exception exception) {
                        throw new IllegalStateException(exception);
                    }
                }
            }
        }

        @Override
        public void flush(long timeoutMillis) {
        }

        @Override
        public RateLimiter getRateLimiter() {
            return rateLimiter;
        }

        @Override
        public void close(boolean isRestarting) {
        }

        @Override
        public void close() {
        }
    }
}
