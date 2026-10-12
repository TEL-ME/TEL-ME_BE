package com.telme.subscription.controller;

import static com.telme.chat.service.HttpSessionChatActorProvider.USER_ID_ATTRIBUTE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// 동시 요청은 실제 커밋이 필요해 트랜잭션 롤백을 쓰지 않고, 만든 데이터는 직접 지운다
@SpringBootTest
@AutoConfigureMockMvc
class SubscriptionApiIntegrationTest {

    static final String URL = "/api/v1/subscriptions";
    // 2026-10-12(월) 정오(KST)
    static final Instant OCT_12 = Instant.parse("2026-10-12T03:00:00Z");
    static final Instant NOV_10 = Instant.parse("2026-11-10T03:00:00Z");

    @MockitoBean Clock clock;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;

    Long userId;
    MockHttpSession session;

    @BeforeEach
    void setUp() {
        at(OCT_12);
        userId = jdbc.queryForObject("INSERT INTO users (email, name) VALUES (?, '구독') RETURNING user_id",
                Long.class, "sub-api-" + UUID.randomUUID() + "@test.com");
        session = loggedIn(userId);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM subscription_histories WHERE subscription_id IN "
                + "(SELECT subscription_id FROM user_subscriptions WHERE user_id = ?)", userId);
        jdbc.update("DELETE FROM user_subscriptions WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM users WHERE user_id = ?", userId);
    }

    @Test
    void 로그인하지_않으면_모든_API가_401() throws Exception {
        Map<String, Object> body = Map.of("planId", planId("FIVE_G_LITE"));

        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isUnauthorized());
        mvc.perform(patch(URL + "/plan").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete(URL)).andExpect(status().isUnauthorized());
        mvc.perform(get(URL + "/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void 가입하지_않은_사용자의_내_요금제는_오류가_아니라_subscribed_false() throws Exception {
        mvc.perform(get(URL + "/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.subscribed").value(false))
                .andExpect(jsonPath("$.result.subscription").doesNotExist());
    }

    @Test
    void 가입하면_바로_현재_요금제가_되고_내_요금제에서_조회된다() throws Exception {
        mvc.perform(subscribe("FIVE_G_STANDARD", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.currentPlan.code").value("FIVE_G_STANDARD"))
                .andExpect(jsonPath("$.result.currentPlan.monthlyFee").value(65000))
                .andExpect(jsonPath("$.result.pendingChange").doesNotExist());

        mvc.perform(get(URL + "/me").session(session))
                .andExpect(jsonPath("$.result.subscribed").value(true))
                .andExpect(jsonPath("$.result.subscription.currentPlan.name").value("5G 스탠다드"));
    }

    @Test
    void 이미_가입했으면_409() throws Exception {
        mvc.perform(subscribe("FIVE_G_LITE", null)).andExpect(status().isOk());

        mvc.perform(subscribe("LTE_BASIC", null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SUBSCRIPTION409-0"));
    }

    @Test
    void 요청_값이_올바르지_않으면_400이고_없는_요금제는_404() throws Exception {
        mvc.perform(post(URL).session(session).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400-1"));
        mvc.perform(post(URL).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":%d,\"birthDate\":\"2010/05/01\"}".formatted(planId("FIVE_G_LITE"))))
                .andExpect(status().isBadRequest());
        mvc.perform(post(URL).session(session).contentType(MediaType.APPLICATION_JSON).content("{\"planId\":999999}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLAN404-0"));
    }

    @Test
    void 청소년_요금제는_생년월일이_필요하고_가입되면_저장된다() throws Exception {
        mvc.perform(subscribe("FIVE_G_YOUTH", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SUBSCRIPTION400-0"));
        mvc.perform(subscribe("FIVE_G_YOUTH", "1990-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SUBSCRIPTION400-2"));
        assertThat(birthDate()).isNull();

        mvc.perform(subscribe("FIVE_G_YOUTH", "2010-05-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.currentPlan.targetGroup").value("YOUTH"));
        assertThat(birthDate()).isEqualTo("2010-05-01");
    }

    @Test
    void 가입한_달에는_변경할_수_없고_다음_달에는_예약된_변경으로_응답한다() throws Exception {
        mvc.perform(subscribe("FIVE_G_LITE", null)).andExpect(status().isOk());

        mvc.perform(change("FIVE_G_STANDARD"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SUBSCRIPTION409-2"));

        at(NOV_10);
        mvc.perform(change("FIVE_G_STANDARD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.currentPlan.code").value("FIVE_G_LITE"))
                .andExpect(jsonPath("$.result.pendingChange.plan.code").value("FIVE_G_STANDARD"))
                .andExpect(jsonPath("$.result.pendingChange.effectiveFrom").value("2026-11-10T15:00:00Z"));

        mvc.perform(get(URL + "/me").session(session))
                .andExpect(jsonPath("$.result.subscription.pendingChange.plan.code").value("FIVE_G_STANDARD"));
        mvc.perform(change("LTE_PLUS"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SUBSCRIPTION409-3"));
    }

    @Test
    void 현재와_같은_요금제로는_변경할_수_없다() throws Exception {
        mvc.perform(subscribe("FIVE_G_LITE", null)).andExpect(status().isOk());
        at(NOV_10);

        mvc.perform(change("FIVE_G_LITE"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SUBSCRIPTION409-1"));
    }

    @Test
    void 해지하면_즉시_미가입_상태가_되고_다시_해지하면_404() throws Exception {
        mvc.perform(subscribe("FIVE_G_LITE", null)).andExpect(status().isOk());

        mvc.perform(delete(URL).session(session)).andExpect(status().isOk());

        mvc.perform(get(URL + "/me").session(session))
                .andExpect(jsonPath("$.result.subscribed").value(false));
        mvc.perform(delete(URL).session(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SUBSCRIPTION404-0"));
        mvc.perform(change("LTE_BASIC"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SUBSCRIPTION404-0"));
    }

    @Test
    void 같은_사용자가_동시에_가입해도_하나만_성공한다() throws Exception {
        List<Integer> statuses = race(
                () -> subscribe("FIVE_G_LITE", null),
                () -> subscribe("LTE_BASIC", null));

        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        assertThat(count("SELECT count(*) FROM user_subscriptions WHERE user_id = ? AND status = 'ACTIVE'"))
                .isEqualTo(1);
        assertThat(count("SELECT count(*) FROM subscription_histories h JOIN user_subscriptions s "
                + "ON s.subscription_id = h.subscription_id WHERE s.user_id = ?")).isEqualTo(1);
    }

    @Test
    void 같은_구독의_변경을_동시에_신청해도_한_번만_받는다() throws Exception {
        mvc.perform(subscribe("FIVE_G_LITE", null)).andExpect(status().isOk());
        at(NOV_10);

        List<Integer> statuses = race(
                () -> change("FIVE_G_STANDARD"),
                () -> change("LTE_PLUS"));

        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        assertThat(count("SELECT count(*) FROM subscription_histories h JOIN user_subscriptions s "
                + "ON s.subscription_id = h.subscription_id WHERE s.user_id = ? AND h.type = 'CHANGE'"))
                .isEqualTo(1);
    }

    @Test
    void 변경과_해지가_동시에_들어와도_이력이_어긋나지_않는다() throws Exception {
        mvc.perform(subscribe("FIVE_G_LITE", null)).andExpect(status().isOk());
        at(NOV_10);

        List<Integer> statuses = race(
                () -> change("FIVE_G_STANDARD"),
                () -> delete(URL).session(session));

        // 먼저 처리된 쪽이 성공하고, 해지가 먼저면 변경은 404, 변경이 먼저면 둘 다 성공
        assertThat(statuses).contains(200).doesNotContain(500);
        assertThat(count("SELECT count(*) FROM user_subscriptions WHERE user_id = ? AND status = 'ACTIVE'"))
                .isZero();
    }

    private List<Integer> race(Supplier<MockHttpServletRequestBuilder> first,
            Supplier<MockHttpServletRequestBuilder> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<MvcResult> a = executor.submit(() -> send(first, ready, start));
            Future<MvcResult> b = executor.submit(() -> send(second, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(
                    a.get(10, TimeUnit.SECONDS).getResponse().getStatus(),
                    b.get(10, TimeUnit.SECONDS).getResponse().getStatus());
        } finally {
            executor.shutdownNow();
        }
    }

    private MvcResult send(Supplier<MockHttpServletRequestBuilder> request, CountDownLatch ready, CountDownLatch start)
            throws Exception {
        ready.countDown();
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        return mvc.perform(request.get()).andReturn();
    }

    private MockHttpServletRequestBuilder subscribe(String planCode, String birthDate) {
        return post(URL).session(session).contentType(MediaType.APPLICATION_JSON).content(planBody(planCode, birthDate));
    }

    private MockHttpServletRequestBuilder change(String planCode) {
        return patch(URL + "/plan").session(session).contentType(MediaType.APPLICATION_JSON)
                .content(planBody(planCode, null));
    }

    private String planBody(String planCode, String birthDate) {
        Map<String, Object> body = new HashMap<>();
        body.put("planId", planId(planCode));
        if (birthDate != null) {
            body.put("birthDate", birthDate);
        }
        return json(body);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void at(Instant instant) {
        when(clock.instant()).thenReturn(instant);
    }

    private Long planId(String code) {
        return jdbc.queryForObject("SELECT plan_id FROM plans WHERE code = ?", Long.class, code);
    }

    private String birthDate() {
        return jdbc.queryForObject("SELECT birth_date::text FROM users WHERE user_id = ?", String.class, userId);
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class, userId);
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
