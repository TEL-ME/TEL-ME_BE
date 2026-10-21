package com.telme.chat.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.service.ChatProcessingPort;
import com.telme.chat.service.HttpSessionChatActorProvider;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

// 동시 전송까지 확인하려고 테스트 트랜잭션 없이 실제로 커밋하고 끝나면 지운다. 한도는 3으로 줄여 확인한다
@SpringBootTest(properties = "chat.guest-limit.daily-messages=3")
@AutoConfigureMockMvc
class GuestDailyChatLimitIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // 접수까지만 확인한다. 답변 생성은 돌리지 않는다
    @MockitoBean
    private ChatProcessingPort chatProcessingPort;

    private final List<UUID> guests = new ArrayList<>();
    private final List<Long> users = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (UUID guestId : guests) {
            jdbcTemplate.update("delete from chat_sessions where guest_id = ?", guestId);
            jdbcTemplate.update("delete from guests where guest_id = ?", guestId);
        }
        for (Long userId : users) {
            jdbcTemplate.update("delete from chat_sessions where user_id = ?", userId);
            jdbcTemplate.update("delete from chat_input_guard_states where user_id = ?", userId);
            jdbcTemplate.update("delete from users where user_id = ?", userId);
        }
    }

    @Test
    @DisplayName("게스트는 하루 한도까지 질문하고, 넘으면 CHAT429-0을 받으며 질문이 저장되지 않는다")
    void 한도를_넘으면_거절한다() throws Exception {
        MockHttpSession guest = guestSession(persistGuest());

        for (int i = 1; i <= 3; i++) {
            assertThat(send(guest, "요금제 질문 " + i).getResponse().getStatus()).isEqualTo(201);
        }

        MvcResult rejected = send(guest, "요금제 질문 4");
        assertThat(rejected.getResponse().getStatus()).isEqualTo(429);
        assertThat(body(rejected).path("code").asText()).isEqualTo("CHAT429-0");
        assertThat(questionCount(guest)).isEqualTo(3);
    }

    @Test
    @DisplayName("어제(한국 시간) 보낸 질문은 오늘 한도에 들어가지 않는다")
    void 어제_질문은_세지_않는다() throws Exception {
        MockHttpSession guest = guestSession(persistGuest());
        for (int i = 1; i <= 3; i++) {
            send(guest, "어제 질문 " + i);
        }
        jdbcTemplate.update("""
                update chat_messages set created_at = created_at - interval '1 day'
                where session_id in (select session_id from chat_sessions where guest_id = ?)
                """, guestId(guest));

        assertThat(send(guest, "오늘 첫 질문").getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    @DisplayName("한도는 게스트마다 따로 세고 회원은 제한하지 않는다")
    void 다른_게스트와_회원은_영향받지_않는다() throws Exception {
        MockHttpSession full = guestSession(persistGuest());
        for (int i = 1; i <= 3; i++) {
            send(full, "질문 " + i);
        }
        assertThat(send(full, "넘는 질문").getResponse().getStatus()).isEqualTo(429);

        assertThat(send(guestSession(persistGuest()), "다른 게스트 질문").getResponse().getStatus())
                .isEqualTo(201);

        MockHttpSession member = memberSession(persistUser());
        for (int i = 1; i <= 4; i++) {
            assertThat(send(member, "회원 질문 " + i).getResponse().getStatus()).isEqualTo(201);
        }
    }

    @Test
    @DisplayName("입력 검사에서 막힌 메시지는 한도에 들어가지 않는다")
    void 입력_검사_거절은_세지_않는다() throws Exception {
        MockHttpSession guest = guestSession(persistGuest());
        assertThat(send(guest, "씨발").getResponse().getStatus()).isEqualTo(200);

        for (int i = 1; i <= 3; i++) {
            assertThat(send(guest, "정상 질문 " + i).getResponse().getStatus()).isEqualTo(201);
        }
    }

    @Test
    @DisplayName("입력 검사 영수증이 남은 요청을 같은 requestId로 다시 보내면 한도와 관계없이 처음 응답을 돌려준다")
    void 같은_요청_재전송은_한도에_막히지_않는다() throws Exception {
        MockHttpSession guest = guestSession(persistGuest());
        send(guest, "질문 1");
        send(guest, "질문 2");
        long sessionId = createSession(guest);
        UUID requestId = UUID.randomUUID();
        // 영수증은 마스킹·거절처럼 검사에 걸린 요청만 남긴다. 마스킹은 정상 접수라 한도의 마지막 1회를 쓴다
        MvcResult first = postMessage(guest, sessionId, "카드 4111111111111111 납부 문의", requestId);
        assertThat(first.getResponse().getStatus()).isEqualTo(201);

        MvcResult repeated = postMessage(guest, sessionId, "카드 4111111111111111 납부 문의", requestId);
        assertThat(repeated.getResponse().getStatus()).isEqualTo(201);
        assertThat(body(repeated).path("result").path("messageId").asLong())
                .isEqualTo(body(first).path("result").path("messageId").asLong());
        assertThat(questionCount(guest)).isEqualTo(3);
    }

    @Test
    @DisplayName("한도 직전에 같은 게스트가 동시에 두 번 보내면 하나만 접수된다")
    void 동시_전송은_하나만_접수한다() throws Exception {
        MockHttpSession guest = guestSession(persistGuest());
        send(guest, "질문 1");
        send(guest, "질문 2");
        long firstSession = createSession(guest);
        long secondSession = createSession(guest);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<MvcResult> first = executor.submit(() -> postWhenReady(guest, firstSession, ready, start));
            Future<MvcResult> second = executor.submit(() -> postWhenReady(guest, secondSession, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(
                    first.get(10, TimeUnit.SECONDS).getResponse().getStatus(),
                    second.get(10, TimeUnit.SECONDS).getResponse().getStatus()))
                    .containsExactlyInAnyOrder(201, 429);
            assertThat(questionCount(guest)).isEqualTo(3);
        } finally {
            executor.shutdownNow();
        }
    }

    // 세션마다 실행 중인 답변이 있으면 409가 나므로 질문마다 새 세션에 보낸다
    private MvcResult send(MockHttpSession actor, String content) throws Exception {
        return postMessage(actor, createSession(actor), content, null);
    }

    private MvcResult postWhenReady(MockHttpSession actor, long sessionId, CountDownLatch ready, CountDownLatch start)
            throws Exception {
        ready.countDown();
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        return postMessage(actor, sessionId, "동시 질문", null);
    }

    private MvcResult postMessage(MockHttpSession actor, long sessionId, String content, UUID requestId) throws Exception {
        return mockMvc.perform(post("/api/v1/chat/sessions/{sessionId}/messages", sessionId)
                        .session(actor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MessageRequest(content, requestId))))
                .andReturn();
    }

    private long createSession(MockHttpSession actor) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/chat/sessions")
                        .session(actor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"게스트 한도\"}"))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        return body(result).path("result").path("sessionId").asLong();
    }

    private long questionCount(MockHttpSession guest) {
        return jdbcTemplate.queryForObject("""
                select count(*) from chat_messages message
                join chat_sessions session on session.session_id = message.session_id
                where session.guest_id = ? and message.message_type = 'QUESTION'
                """, Long.class, guestId(guest));
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private UUID persistGuest() {
        UUID guestId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbcTemplate.update(
                "insert into guests (guest_id, created_at, last_seen_at, expires_at) values (?, ?, ?, ?)",
                guestId, java.sql.Timestamp.from(now), java.sql.Timestamp.from(now),
                java.sql.Timestamp.from(now.plusSeconds(86_400)));
        guests.add(guestId);
        return guestId;
    }

    private long persistUser() {
        Long userId = jdbcTemplate.queryForObject(
                "insert into users (email, name) values (?, ?) returning user_id",
                Long.class, "guest-limit-" + UUID.randomUUID() + "@example.com", "guest-limit");
        users.add(userId);
        return userId;
    }

    private static MockHttpSession guestSession(UUID guestId) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionChatActorProvider.GUEST_ID_ATTRIBUTE, guestId);
        return session;
    }

    private static MockHttpSession memberSession(long userId) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionChatActorProvider.USER_ID_ATTRIBUTE, userId);
        return session;
    }

    private static UUID guestId(MockHttpSession session) {
        return (UUID) session.getAttribute(HttpSessionChatActorProvider.GUEST_ID_ATTRIBUTE);
    }

    private record MessageRequest(String content, UUID requestId) {
    }
}
