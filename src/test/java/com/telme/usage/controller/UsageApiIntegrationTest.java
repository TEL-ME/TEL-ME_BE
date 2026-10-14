package com.telme.usage.controller;

import static com.telme.chat.service.HttpSessionChatActorProvider.USER_ID_ATTRIBUTE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.telme.usage.service.GeneratedUsage;
import com.telme.usage.service.UsageGenerator;
import com.telme.usage.service.UsageLimits;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// 첫 조회 때 사용량을 만들어 저장하므로 동시 요청은 실제 커밋이 필요해 트랜잭션 롤백을 쓰지 않고, 만든 데이터는 직접 지운다
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "telme.subscription.demo-mode=false")
class UsageApiIntegrationTest {

    static final String URL = "/api/v1/usage/me";
    // 2026-10-12(월) 정오(KST)에 가입하면 10월 13일 09:00(KST)에 개통된다
    static final Instant OCT_12 = Instant.parse("2026-10-12T03:00:00Z");
    // 2026-11-10 정오(KST): 11월 중 10일째, 말일(30일)까지 20일
    static final Instant NOV_10 = Instant.parse("2026-11-10T03:00:00Z");
    static final LocalDate NOVEMBER = LocalDate.of(2026, 11, 1);
    // LTE 베이직: 4GB, 통화 700분, 문자 무제한
    static final UsageLimits LTE_BASIC = new UsageLimits(4096L, 700L, null);

    @MockitoBean Clock clock;

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UsageGenerator generator;

    Long userId;
    MockHttpSession session;

    @BeforeEach
    void setUp() {
        at(OCT_12);
        userId = jdbc.queryForObject("INSERT INTO users (email, name) VALUES (?, '사용량') RETURNING user_id",
                Long.class, "usage-api-" + UUID.randomUUID() + "@test.com");
        session = loggedIn(userId);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM subscription_usages WHERE subscription_id IN "
                + "(SELECT subscription_id FROM user_subscriptions WHERE user_id = ?)", userId);
        jdbc.update("DELETE FROM subscription_pauses WHERE subscription_id IN "
                + "(SELECT subscription_id FROM user_subscriptions WHERE user_id = ?)", userId);
        jdbc.update("DELETE FROM subscription_histories WHERE subscription_id IN "
                + "(SELECT subscription_id FROM user_subscriptions WHERE user_id = ?)", userId);
        jdbc.update("DELETE FROM user_subscriptions WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM users WHERE user_id = ?", userId);
    }

    @Test
    void 로그인하지_않으면_401() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    @Test
    void 가입하지_않았으면_오류가_아니라_NOT_SUBSCRIBED() throws Exception {
        mvc.perform(get(URL).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("NOT_SUBSCRIBED"))
                .andExpect(jsonPath("$.result.usage").doesNotExist());
        assertThat(usageRows()).isZero();
    }

    @Test
    void 개통_전에는_개통_예정_시각과_함께_PENDING_ACTIVATION이고_사용량을_만들지_않는다() throws Exception {
        subscribe("LTE_BASIC", false);

        mvc.perform(get(URL).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("PENDING_ACTIVATION"))
                .andExpect(jsonPath("$.result.activatesAt").value("2026-10-13T00:00:00Z"))
                .andExpect(jsonPath("$.result.usage").doesNotExist());
        assertThat(usageRows()).isZero();
    }

    @Test
    void 개통_후에는_요금제_제공량과_생성된_사용량으로_ACTIVE_응답을_만든다() throws Exception {
        subscribe("LTE_BASIC", false);
        at(NOV_10);
        // LTE 베이직: 데이터 제공량은 DB 값, 통화 700분, 문자 무제한
        long dataLimitMb = jdbc.queryForObject(
                "SELECT (data_gb * 1024)::bigint FROM plans WHERE code = 'LTE_BASIC'", Long.class);
        GeneratedUsage november = generator.generate(userId, NOVEMBER, LTE_BASIC);

        mvc.perform(get(URL).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("ACTIVE"))
                .andExpect(jsonPath("$.result.activatesAt").doesNotExist())
                .andExpect(jsonPath("$.result.usage.month").value("2026-11-01"))
                .andExpect(jsonPath("$.result.usage.daysUntilBilling").value(20))
                .andExpect(jsonPath("$.result.usage.paused").value(false))
                .andExpect(jsonPath("$.result.usage.data.used").value(Math.round(november.dataMb() * 10 / 30.0)))
                .andExpect(jsonPath("$.result.usage.data.limit").value(dataLimitMb))
                .andExpect(jsonPath("$.result.usage.data.unlimited").value(false))
                .andExpect(jsonPath("$.result.usage.voice.used").value(Math.round(november.voiceMinutes() * 10 / 30.0)))
                .andExpect(jsonPath("$.result.usage.voice.limit").value(700))
                .andExpect(jsonPath("$.result.usage.sms.used").value(Math.round(november.smsCount() * 10 / 30.0)))
                .andExpect(jsonPath("$.result.usage.sms.unlimited").value(true))
                .andExpect(jsonPath("$.result.usage.sms.limit").doesNotExist())
                .andExpect(jsonPath("$.result.usage.sms.percent").doesNotExist())
                .andExpect(jsonPath("$.result.usage.expectedFee.amount").isNumber())
                .andExpect(jsonPath("$.result.usage.expectedFee.previousAmount").isNumber());
    }

    @Test
    void 월을_고르면_그달_사용량과_조회_가능_범위를_주고_이번_달_전용_값은_없다() throws Exception {
        subscribe("LTE_BASIC", false);
        at(NOV_10);
        GeneratedUsage october = generator.generate(userId, LocalDate.of(2026, 10, 1), LTE_BASIC);

        // 10월은 개통한 달(13일 개통)이라 13~31일 19일분만 반영
        mvc.perform(get(URL).param("month", "2026-10").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("ACTIVE"))
                .andExpect(jsonPath("$.result.availableFrom").value("2026-10-01"))
                .andExpect(jsonPath("$.result.availableTo").value("2026-11-01"))
                .andExpect(jsonPath("$.result.usage.month").value("2026-10-01"))
                .andExpect(jsonPath("$.result.usage.data.used").value(Math.round(october.dataMb() * 19 / 31.0)))
                .andExpect(jsonPath("$.result.usage.voice.limit").value(700))
                .andExpect(jsonPath("$.result.usage.daysUntilBilling").doesNotExist())
                .andExpect(jsonPath("$.result.usage.paused").doesNotExist())
                .andExpect(jsonPath("$.result.usage.expectedFee").doesNotExist());
    }

    @Test
    void 조회할_수_없는_달과_잘못된_형식은_400() throws Exception {
        subscribe("LTE_BASIC", false);
        at(NOV_10);

        mvc.perform(get(URL).param("month", "2026-09").session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("USAGE400-0"));
        mvc.perform(get(URL).param("month", "2026-12").session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("USAGE400-0"));
        mvc.perform(get(URL).param("month", "2026-13").session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400-1"));
        mvc.perform(get(URL).param("month", "abc").session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400-1"));
    }

    @Test
    void 같은_달에_다시_조회해도_저장된_값이라_응답이_같고_행이_늘지_않는다() throws Exception {
        subscribe("FIVE_G_STANDARD", false);
        at(NOV_10);

        String first = mvc.perform(get(URL).session(session)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        int rows = usageRows();
        String second = mvc.perform(get(URL).session(session)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(second).isEqualTo(first);
        assertThat(usageRows()).isEqualTo(rows).isEqualTo(2);
    }

    @Test
    void 개통한_달과_이번_달만_만들어지고_개통_이전_달은_만들지_않는다() throws Exception {
        subscribe("FIVE_G_STANDARD", false);
        at(NOV_10);

        mvc.perform(get(URL).session(session)).andExpect(status().isOk());

        assertThat(jdbc.queryForList("SELECT usage_month::text FROM subscription_usages u JOIN user_subscriptions s "
                + "ON s.subscription_id = u.subscription_id WHERE s.user_id = ? ORDER BY usage_month",
                String.class, userId)).containsExactly("2026-10-01", "2026-11-01");
    }

    @Test
    void 처음_조회가_동시에_들어와도_행은_한_번만_만들어지고_응답이_같다() throws Exception {
        subscribe("FIVE_G_STANDARD", false);
        at(NOV_10);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<String>> results = List.of(
                    executor.submit(() -> concurrentGet(ready, start)),
                    executor.submit(() -> concurrentGet(ready, start)));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(results.get(0).get(10, TimeUnit.SECONDS)).isEqualTo(results.get(1).get(10, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
        assertThat(usageRows()).isEqualTo(2);
    }

    @Test
    void 일시_정지_중이면_paused가_true이고_사용량은_그대로다() throws Exception {
        subscribe("FIVE_G_STANDARD", false);
        at(NOV_10);
        String before = usageOf(mvc.perform(get(URL).session(session)).andReturn().getResponse().getContentAsString());

        mvc.perform(post("/api/v1/subscriptions/pause").session(session).contentType(MediaType.APPLICATION_JSON)
                .content("{\"days\":7}")).andExpect(status().isOk());

        String body = mvc.perform(get(URL).session(session))
                .andExpect(jsonPath("$.result.usage.paused").value(true))
                .andReturn().getResponse().getContentAsString();
        assertThat(usageOf(body)).isEqualTo(before);
    }

    @Test
    void 해지하면_다시_NOT_SUBSCRIBED() throws Exception {
        subscribe("FIVE_G_STANDARD", false);
        at(NOV_10);
        mvc.perform(get(URL).session(session)).andExpect(jsonPath("$.result.status").value("ACTIVE"));

        mvc.perform(delete("/api/v1/subscriptions").session(session)).andExpect(status().isOk());

        mvc.perform(get(URL).session(session))
                .andExpect(jsonPath("$.result.status").value("NOT_SUBSCRIBED"))
                .andExpect(jsonPath("$.result.usage").doesNotExist());
    }

    private String concurrentGet(CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        return mvc.perform(get(URL).session(session)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    // 데이터·통화·문자 사용량 부분만 비교하려고 expectedFee 이전까지 자른다
    private String usageOf(String body) {
        return body.substring(body.indexOf("\"data\""), body.indexOf("\"expectedFee\""));
    }

    private void subscribe(String planCode, boolean contract) throws Exception {
        Long planId = jdbc.queryForObject("SELECT plan_id FROM plans WHERE code = ?", Long.class, planCode);
        mvc.perform(post("/api/v1/subscriptions").session(session).contentType(MediaType.APPLICATION_JSON)
                .content("{\"planId\":%d,\"contract\":%b}".formatted(planId, contract))).andExpect(status().isOk());
    }

    private int usageRows() {
        return jdbc.queryForObject("SELECT count(*) FROM subscription_usages u JOIN user_subscriptions s "
                + "ON s.subscription_id = u.subscription_id WHERE s.user_id = ?", Integer.class, userId);
    }

    private void at(Instant instant) {
        when(clock.instant()).thenReturn(instant);
    }

    private MockHttpSession loggedIn(Long id) {
        MockHttpSession httpSession = new MockHttpSession();
        httpSession.setAttribute(USER_ID_ATTRIBUTE, id);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(
                id, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        httpSession.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return httpSession;
    }
}
