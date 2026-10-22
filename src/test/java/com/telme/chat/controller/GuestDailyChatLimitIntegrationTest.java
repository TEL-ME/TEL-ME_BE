package com.telme.chat.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.repository.ChatMessageRepository;
import com.telme.chat.service.ChatProcessingPort;
import com.telme.chat.service.HttpSessionChatActorProvider;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

// 동시 전송까지 확인하려고 테스트 트랜잭션 없이 실제로 커밋하고 끝나면 지운다. 한도는 3으로 줄여 확인한다
@SpringBootTest(properties = "chat.guest-limit.daily-messages=3")
@AutoConfigureMockMvc
class GuestDailyChatLimitIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

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
        assertThat(guestLimit(guest).path("remaining").asInt()).isEqualTo(3);

        for (int i = 1; i <= 3; i++) {
            MvcResult accepted = send(guest, "요금제 질문 " + i);
            assertThat(accepted.getResponse().getStatus()).isEqualTo(201);
            assertThat(body(accepted).path("result").path("guestQuestionsRemaining").asInt()).isEqualTo(3 - i);
        }
        assertThat(guestLimit(guest).path("remaining").asInt()).isZero();

        MvcResult rejected = send(guest, "요금제 질문 4");
        assertThat(rejected.getResponse().getStatus()).isEqualTo(429);
        assertThat(body(rejected).path("code").asText()).isEqualTo("CHAT429-0");
        assertThat(questionCount(guest)).isEqualTo(3);
    }

    // 하루를 통째로 당기면 KST로 자르든 UTC로 자르든 '어제'라서 경계를 못 잡는다. KST 0시 바로 앞뒤를 둘 다 찍어야
    // UTC로 잘못 자를 때(경계가 KST 오전 9시) 테스트를 돌리는 시각과 관계없이 한쪽이 틀린다.
    // 세션 시간대를 UTC로 두어 로컬 DB(TZ=Asia/Seoul)에서도 운영 RDS·CI(UTC)와 같은 조건으로 확인한다
    @ParameterizedTest(name = "DB 세션 시간대 {0}")
    @ValueSource(strings = {"UTC", "Asia/Seoul"})
    @DisplayName("한국 시간 0시 1초 전 질문은 세지 않고, 0시 1초 후 질문은 센다")
    void 한국_시간_0시로_하루를_자른다(String sessionTimeZone) {
        UUID guestId = persistGuest();
        long sessionId = persistSession(guestId);
        Instant todayStart = LocalDate.now(KST).atStartOfDay(KST).toInstant();
        persistQuestion(sessionId, todayStart.minusSeconds(1));
        persistQuestion(sessionId, todayStart.minusSeconds(1));
        persistQuestion(sessionId, todayStart.plusSeconds(1));

        Long counted = transactionTemplate.execute(status -> {
            jdbcTemplate.execute("set local time zone '" + sessionTimeZone + "'");
            return chatMessageRepository.countGuestQuestionsToday(guestId);
        });

        assertThat(counted).isEqualTo(1);
    }

    @Test
    @DisplayName("서버 쪽 실패나 취소로 답을 받지 못한 질문은 한도에 들어가지 않는다")
    void 실패한_질문은_세지_않는다() throws Exception {
        MockHttpSession guest = guestSession(persistGuest());
        for (int i = 1; i <= 3; i++) {
            assertThat(send(guest, "질문 " + i).getResponse().getStatus()).isEqualTo(201);
        }
        assertThat(send(guest, "넘는 질문").getResponse().getStatus()).isEqualTo(429);

        setExecutionStatus(guest, "질문 1", "FAILED");
        setExecutionStatus(guest, "질문 2", "CANCELLED");

        assertThat(send(guest, "다시 보낸 질문 1").getResponse().getStatus()).isEqualTo(201);
        assertThat(send(guest, "다시 보낸 질문 2").getResponse().getStatus()).isEqualTo(201);
        assertThat(send(guest, "또 넘는 질문").getResponse().getStatus()).isEqualTo(429);
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
            MvcResult accepted = send(member, "회원 질문 " + i);
            assertThat(accepted.getResponse().getStatus()).isEqualTo(201);
            assertThat(body(accepted).path("result").has("guestQuestionsRemaining")).isFalse();
        }
        JsonNode memberLimit = guestLimit(member);
        assertThat(memberLimit.path("limited").asBoolean()).isFalse();
        assertThat(memberLimit.path("remaining").isNull()).isTrue();
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

    private JsonNode guestLimit(MockHttpSession actor) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/chat/guest-limit").session(actor)).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return body(result).path("result");
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

    // 경계 시각을 정확히 찍으려고 API를 거치지 않고 질문을 직접 넣는다
    private long persistSession(UUID guestId) {
        return jdbcTemplate.queryForObject(
                "insert into chat_sessions (guest_id, title) values (?, '게스트 한도') returning session_id",
                Long.class, guestId);
    }

    private void persistQuestion(long sessionId, Instant createdAt) {
        jdbcTemplate.update("""
                insert into chat_messages (session_id, sequence_no, role, message_type, content, status, created_at)
                values (?, (select coalesce(max(sequence_no), 0) + 1 from chat_messages where session_id = ?),
                        'USER', 'QUESTION', '경계 질문', 'COMPLETED', ?)
                """, sessionId, sessionId, Timestamp.from(createdAt));
    }

    private void setExecutionStatus(MockHttpSession guest, String question, String status) {
        int updated = jdbcTemplate.update("""
                update chat_executions set status = ?
                where input_message_id in (
                    select message.message_id from chat_messages message
                    join chat_sessions session on session.session_id = message.session_id
                    where session.guest_id = ? and message.content = ?)
                """, status, guestId(guest), question);
        assertThat(updated).isEqualTo(1);
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private UUID persistGuest() {
        UUID guestId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbcTemplate.update(
                "insert into guests (guest_id, created_at, last_seen_at, expires_at) values (?, ?, ?, ?)",
                guestId, Timestamp.from(now), Timestamp.from(now),
                Timestamp.from(now.plusSeconds(86_400)));
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
