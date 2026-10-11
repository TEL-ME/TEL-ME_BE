package com.telme.chat.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.entity.ChatSession;
import com.telme.chat.service.HttpSessionChatActorProvider;
import com.telme.member.entity.Guest;
import com.telme.member.entity.User;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {"llm.provider=fake", "chat.title.enabled=false"})
@AutoConfigureMockMvc
@Transactional
class ChatInputGuardStatusApiIntegrationTest {

    private static final String PATH = "/api/v1/chat/input-guard/status";
    private static final Instant NOW = Instant.parse("2026-10-08T00:00:00Z");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ObjectMapper mapper;

    @MockitoBean
    private Clock clock;

    private final AtomicReference<Instant> current = new AtomicReference<>(NOW);
    private User owner;
    private MockHttpSession identity;

    @BeforeEach
    void 현재_사용자와_서버_시각을_준비한다() {
        when(clock.instant()).thenAnswer(invocation -> current.get());
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        owner = 회원을_저장한다();
        identity = 회원_세션(owner);
    }

    @Test
    @DisplayName("제한 상태 행이 없는 회원은 미제한이며 조회가 행을 만들지 않는다")
    void 기록이_없는_회원의_조회는_상태를_생성하지_않는다() throws Exception {
        var before = 저장_상태();

        mvc.perform(get(PATH).session(identity))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().doesNotExist("Retry-After"))
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.restricted").value(false))
                .andExpect(jsonPath("$.result.restrictionUntil").isEmpty())
                .andExpect(jsonPath("$.result.retryAfterSeconds").value(0))
                .andExpect(jsonPath("$.result.serverTime").value(NOW.toString()));

        assertThat(저장_상태()).isEqualTo(before);
    }

    @Test
    @DisplayName("회원의 유효한 제한은 종료 시각과 올림한 남은 시간 및 헤더를 반환한다")
    void 제한_중인_회원의_남은_시간을_반환한다() throws Exception {
        제한을_저장한다(owner.getUserId(), null, NOW.plusSeconds(40).plusMillis(1));

        mvc.perform(get(PATH).session(identity))
                .andExpect(status().isOk())
                .andExpect(header().string("Retry-After", "41"))
                .andExpect(jsonPath("$.result.restricted").value(true))
                .andExpect(jsonPath("$.result.retryAfterSeconds").value(41))
                .andExpect(jsonPath("$.result.restrictionUntil")
                        .value(NOW.plusSeconds(40).plusMillis(1).toString()));
    }

    @Test
    @DisplayName("정확한 종료 시각의 조회는 미제한이며 저장된 만료 상태를 초기화하지 않는다")
    void 정확한_종료_시각에는_읽기만_하고_미제한을_반환한다() throws Exception {
        제한을_저장한다(owner.getUserId(), null, NOW);

        만료_상태가_바뀌지_않는지_확인한다();
    }

    @Test
    @DisplayName("이미 지난 제한의 조회도 감지 이력이나 집계 구간을 변경하지 않는다")
    void 지난_제한을_조회해도_집계_상태를_초기화하지_않는다() throws Exception {
        제한을_저장한다(owner.getUserId(), null, NOW.minusSeconds(1));

        만료_상태가_바뀌지_않는지_확인한다();
    }

    @Test
    @DisplayName("게스트는 본인 제한만 조회하고 다른 게스트의 종료 시각은 사용하지 않는다")
    void 다른_게스트의_제한은_섞이지_않는다() throws Exception {
        Guest mine = 게스트를_저장한다(null);
        Guest other = 게스트를_저장한다(null);
        제한을_저장한다(null, mine.getGuestId(), NOW.plusSeconds(10));
        제한을_저장한다(null, other.getGuestId(), NOW.plusSeconds(60));

        mvc.perform(get(PATH).session(게스트_세션(mine)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.retryAfterSeconds").value(10));
    }

    @Test
    @DisplayName("회원은 사용자 ID를 쿼리로 보내도 다른 회원의 제한을 조회할 수 없다")
    void 쿼리_식별자로_조회_대상을_바꿀_수_없다() throws Exception {
        User other = 회원을_저장한다();
        Guest otherGuest = 게스트를_저장한다(other);
        제한을_저장한다(other.getUserId(), null, NOW.plusSeconds(60));
        제한을_저장한다(null, otherGuest.getGuestId(), NOW.plusSeconds(60));

        mvc.perform(get(PATH).session(identity)
                        .param("userId", other.getUserId().toString())
                        .param("guestId", otherGuest.getGuestId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.restricted").value(false))
                .andExpect(jsonPath("$.result.retryAfterSeconds").value(0));
    }

    @Test
    @DisplayName("회원 상태가 없어도 승계 게스트의 제한을 읽고 새 회원 상태를 만들지 않는다")
    void 승계_제한을_조회만_해도_상태_행은_추가되지_않는다() throws Exception {
        Guest merged = 게스트를_저장한다(owner);
        제한을_저장한다(null, merged.getGuestId(), NOW.plusSeconds(60));
        var before = 저장_상태();

        mvc.perform(get(PATH).session(identity))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.restricted").value(true))
                .andExpect(jsonPath("$.result.retryAfterSeconds").value(60));

        assertThat(저장_상태()).isEqualTo(before);
    }

    @Test
    @DisplayName("회원으로 승계된 게스트 식별자도 기존 접수와 같은 회원 제한을 조회한다")
    void 승계된_게스트_세션의_조회도_회원_범위를_사용한다() throws Exception {
        Guest merged = 게스트를_저장한다(owner);
        제한을_저장한다(owner.getUserId(), null, NOW.plusSeconds(20));
        제한을_저장한다(null, merged.getGuestId(), NOW.plusSeconds(60));

        mvc.perform(get(PATH).session(게스트_세션(merged)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.retryAfterSeconds").value(20));
    }

    @Test
    @DisplayName("회원 본인 제한이 만료되면 승계 게스트 중 가장 늦은 유효한 종료를 조회한다")
    void 만료된_본인_제한은_유효한_승계_제한으로_판단한다() throws Exception {
        Guest first = 게스트를_저장한다(owner);
        Guest second = 게스트를_저장한다(owner);
        Guest unrelated = 게스트를_저장한다(회원을_저장한다());
        제한을_저장한다(owner.getUserId(), null, NOW);
        제한을_저장한다(null, first.getGuestId(), NOW.plusSeconds(20));
        제한을_저장한다(null, second.getGuestId(), NOW.plusSeconds(60));
        제한을_저장한다(null, unrelated.getGuestId(), NOW.plusSeconds(90));

        mvc.perform(get(PATH).session(identity))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.retryAfterSeconds").value(60));
    }

    @Test
    @DisplayName("반복 조회는 감지·상담·메시지·실행을 변경하지 않고 실제 전송은 계속 제한한다")
    void 조회로_제한이_연장되거나_전송_검사가_우회되지_않는다() throws Exception {
        ChatSession chat = ChatSession.builder().userId(owner.getUserId()).build();
        entityManager.persist(chat);
        entityManager.flush();
        제한을_저장한다(owner.getUserId(), null, NOW.plusSeconds(60));
        var before = 저장_상태();

        mvc.perform(get(PATH).session(identity))
                .andExpect(jsonPath("$.result.retryAfterSeconds").value(60));
        current.set(NOW.plusSeconds(20));
        mvc.perform(get(PATH).session(identity))
                .andExpect(jsonPath("$.result.retryAfterSeconds").value(40));
        assertThat(저장_상태()).isEqualTo(before);

        mvc.perform(post("/api/v1/chat/sessions/{sessionId}/messages", chat.getSessionId())
                        .session(identity)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"문의합니다\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.executionId").doesNotExist())
                .andExpect(jsonPath("$.result.inputGuard.action").value("RESTRICTED"))
                .andExpect(jsonPath("$.result.inputGuard.retryAfterSeconds").value(40));

        assertThat(저장_상태()).isEqualTo(before);
    }

    @ParameterizedTest
    @DisplayName("나노초 시각의 새 제한도 최초 응답·조회·재전송·이력에 같은 DB 시각을 사용한다")
    @CsvSource({
            "237818400, 2026-10-08T00:01:00.237818Z",
            "237818600, 2026-10-08T00:01:00.237818Z",
            "999999600, 2026-10-08T00:01:00.999999Z"
    })
    void 새_제한의_저장_시각과_모든_응답이_일치한다(long nanos, String expectedUntil) throws Exception {
        current.set(NOW.plusNanos(nanos));
        ChatSession chat = ChatSession.builder().userId(owner.getUserId()).build();
        entityManager.persist(chat);
        entityManager.flush();
        String path = "/api/v1/chat/sessions/" + chat.getSessionId() + "/messages";
        UUID requestId = UUID.randomUUID();
        String request = mapper.writeValueAsString(Map.of("content", "ㅅㅂ", "requestId", requestId));
        for (int count = 1; count <= 2; count++) {
            mvc.perform(post(path).session(identity).contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(Map.of(
                                    "content", "ㅅㅂ", "requestId", UUID.randomUUID()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.inputGuard.action").value("WARNED"))
                    .andExpect(jsonPath("$.result.inputGuard.violationCount").value(count));
        }

        var result = mvc.perform(post(path).session(identity).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.result.executionId").doesNotExist())
                .andExpect(jsonPath("$.result.inputGuard.action").value("RESTRICTED"))
                .andExpect(jsonPath("$.result.inputGuard.retryAfterSeconds").value(60))
                .andExpect(jsonPath("$.result.inputGuard.restrictionUntil").value(expectedUntil))
                .andReturn();
        JsonNode sent = mapper.readTree(result.getResponse().getContentAsByteArray()).path("result");
        Instant storedUntil = jdbc.queryForObject(
                "SELECT restriction_until FROM chat_input_guard_states WHERE user_id=?",
                Timestamp.class, owner.getUserId()).toInstant();
        assertThat(storedUntil).isEqualTo(Instant.parse(expectedUntil));
        assertThat(sent.path("inputGuard").path("restrictionStartedAt").asText())
                .isEqualTo(storedUntil.minusSeconds(60).toString());
        JsonNode snapshot = mapper.readTree(jdbc.queryForObject(
                "SELECT response_snapshot::text FROM chat_input_guard_events WHERE request_id=?",
                String.class, requestId));
        assertThat(snapshot).isEqualTo(sent);
        Timestamp eventUntil = jdbc.queryForObject(
                "SELECT restriction_until FROM chat_input_guard_events WHERE request_id=?",
                Timestamp.class, requestId);
        assertThat(eventUntil.toInstant()).isEqualTo(storedUntil);
        var before = 저장_상태();

        mvc.perform(get(PATH).session(identity))
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.result.restrictionUntil").value(expectedUntil))
                .andExpect(jsonPath("$.result.retryAfterSeconds").value(60))
                .andExpect(jsonPath("$.result.serverTime").value(current.get().toString()));
        var repeated = mvc.perform(post(path).session(identity)
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(mapper.readTree(repeated.getResponse().getContentAsByteArray()).path("result"))
                .isEqualTo(sent);
        assertThat(저장_상태()).isEqualTo(before);

        current.set(storedUntil.minusNanos(1));
        mvc.perform(get(PATH).session(identity))
                .andExpect(jsonPath("$.result.restricted").value(true))
                .andExpect(header().string("Retry-After", "1"));
        current.set(storedUntil);
        mvc.perform(get(PATH).session(identity))
                .andExpect(jsonPath("$.result.restricted").value(false))
                .andExpect(jsonPath("$.result.retryAfterSeconds").value(0))
                .andExpect(header().doesNotExist("Retry-After"));
        assertThat(저장_상태()).isEqualTo(before);
    }

    @ParameterizedTest
    @DisplayName("회원 본인 제한 또는 승계 제한은 조회와 실제 전송 검사에서 동일하다")
    @ValueSource(strings = {"유효", "만료", "없음"})
    void 본인_상태별_승계_제한은_실제_접수_판정과_일치한다(String ownStatus) throws Exception {
        ChatSession chat = ChatSession.builder().userId(owner.getUserId()).build();
        entityManager.persist(chat);
        entityManager.flush();
        Guest first = 게스트를_저장한다(owner);
        Guest second = 게스트를_저장한다(owner);
        제한을_저장한다(null, first.getGuestId(), NOW.plusSeconds(20));
        제한을_저장한다(null, second.getGuestId(), NOW.plusSeconds(60));
        if (ownStatus.equals("유효")) {
            제한을_저장한다(owner.getUserId(), null, NOW.plusSeconds(10));
        } else if (ownStatus.equals("만료")) {
            제한을_저장한다(owner.getUserId(), null, NOW);
        }
        var before = 저장_상태();

        var getResult = mvc.perform(get(PATH).session(identity))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.restricted").value(true))
                .andReturn();
        JsonNode read = mapper.readTree(getResult.getResponse().getContentAsByteArray()).path("result");
        assertThat(저장_상태()).isEqualTo(before);

        var sendResult = mvc.perform(post("/api/v1/chat/sessions/{sessionId}/messages", chat.getSessionId())
                        .session(identity)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"요금제 문의입니다\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.inputGuard.action").value("RESTRICTED"))
                .andExpect(jsonPath("$.result.executionId").doesNotExist())
                .andReturn();
        JsonNode notice = mapper.readTree(sendResult.getResponse().getContentAsByteArray())
                .path("result").path("inputGuard");

        assertThat(read.path("restrictionUntil")).isEqualTo(notice.path("restrictionUntil"));
        assertThat(read.path("retryAfterSeconds")).isEqualTo(notice.path("retryAfterSeconds"));
        assertThat(read.path("retryAfterSeconds").asLong())
                .isEqualTo(ownStatus.equals("유효") ? 10 : 60);
    }

    @Test
    @DisplayName("회원과 게스트 식별자가 함께 있으면 기존 인증처럼 회원을 우선한다")
    void 회원_세션의_다른_게스트_값으로_제한이_섞이지_않는다() throws Exception {
        Guest unrelated = 게스트를_저장한다(null);
        제한을_저장한다(owner.getUserId(), null, NOW.plusSeconds(20));
        제한을_저장한다(null, unrelated.getGuestId(), NOW.plusSeconds(60));
        identity.setAttribute(HttpSessionChatActorProvider.GUEST_ID_ATTRIBUTE, unrelated.getGuestId());

        mvc.perform(get(PATH).session(identity))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.retryAfterSeconds").value(20));
    }

    @Test
    @DisplayName("경고만 있는 상태를 조회해도 누적 횟수는 유지되고 다음 경고는 2회가 된다")
    void 미제한_조회가_기존_경고_횟수를_초기화하지_않는다() throws Exception {
        ChatSession chat = ChatSession.builder().userId(owner.getUserId()).build();
        entityManager.persist(chat);
        entityManager.flush();
        String sendPath = "/api/v1/chat/sessions/{sessionId}/messages";

        mvc.perform(post(sendPath, chat.getSessionId())
                        .session(identity)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"ㅅㅂ\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.inputGuard.action").value("WARNED"))
                .andExpect(jsonPath("$.result.inputGuard.violationCount").value(1));
        var before = 저장_상태();

        mvc.perform(get(PATH).session(identity))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.restricted").value(false))
                .andExpect(jsonPath("$.result.retryAfterSeconds").value(0));
        assertThat(저장_상태()).isEqualTo(before);

        mvc.perform(post(sendPath, chat.getSessionId())
                        .session(identity)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"ㅅㅂ\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.inputGuard.action").value("WARNED"))
                .andExpect(jsonPath("$.result.inputGuard.violationCount").value(2));
    }

    @Test
    @DisplayName("식별자가 없는 첫 방문은 기존 게스트 발급만 재사용하고 제한 기록을 만들지 않는다")
    void 첫_게스트_조회는_입력_검사_상태를_만들지_않는다() throws Exception {
        var before = 저장_상태();

        var result = mvc.perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.restricted").value(false))
                .andReturn();

        assertThat(result.getRequest().getSession(false)
                .getAttribute(HttpSessionChatActorProvider.GUEST_ID_ATTRIBUTE)).isNotNull();
        assertThat(저장_상태()).isEqualTo(before);
    }

    @Test
    @DisplayName("실제 생성된 API 명세는 네 응답 필드와 제한 여부별 예시를 제공한다")
    void 조회_API_명세와_예시는_실제_응답_계약을_따른다() throws Exception {
        var result = mvc.perform(get("/v3/api-docs").session(identity))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode document = mapper.readTree(result.getResponse().getContentAsByteArray());
        JsonNode response = document.path("paths").path(PATH).path("get")
                .path("responses").path("200");

        assertThat(response.path("headers").has("Retry-After")).isTrue();
        assertThat(response.path("content").path("application/json").path("schema")
                .path("properties").path("result").path("$ref").asText())
                .isEqualTo("#/components/schemas/ChatInputGuardStatusResponse");
        JsonNode properties = document.path("components").path("schemas")
                .path("ChatInputGuardStatusResponse").path("properties");
        assertThat(properties.has("restricted")).isTrue();
        assertThat(properties.has("restrictionUntil")).isTrue();
        assertThat(properties.has("retryAfterSeconds")).isTrue();
        assertThat(properties.has("serverTime")).isTrue();
        JsonNode examples = response.path("content").path("application/json").path("examples");
        JsonNode restricted = 예시_본문(examples, "restricted");
        JsonNode unrestricted = 예시_본문(examples, "unrestricted");
        assertThat(restricted.path("result").path("restricted").asBoolean()).isTrue();
        assertThat(unrestricted.path("result").path("restricted").asBoolean()).isFalse();
        assertThat(unrestricted.path("result").path("restrictionUntil").isNull()).isTrue();
        assertThat(unrestricted.path("result").path("retryAfterSeconds").asLong()).isZero();
    }

    private void 만료_상태가_바뀌지_않는지_확인한다() throws Exception {
        var before = 저장_상태();
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(get(PATH).session(identity))
                    .andExpect(status().isOk())
                    .andExpect(header().doesNotExist("Retry-After"))
                    .andExpect(jsonPath("$.result.restricted").value(false))
                    .andExpect(jsonPath("$.result.restrictionUntil").isEmpty())
                    .andExpect(jsonPath("$.result.retryAfterSeconds").value(0));
        }
        assertThat(저장_상태()).isEqualTo(before);
    }

    private User 회원을_저장한다() {
        User user = User.builder()
                .email("guard-status-" + UUID.randomUUID() + "@example.com")
                .name("조회 검증 사용자")
                .build();
        entityManager.persist(user);
        entityManager.flush();
        return user;
    }

    private Guest 게스트를_저장한다(User member) {
        Guest guest = Guest.builder()
                .guestId(UUID.randomUUID())
                .expiresAt(NOW.plusSeconds(86400))
                .lastSeenAt(NOW)
                .mergedUser(member)
                .build();
        entityManager.persist(guest);
        entityManager.flush();
        return guest;
    }

    private MockHttpSession 회원_세션(User member) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionChatActorProvider.USER_ID_ATTRIBUTE, member.getUserId());
        return session;
    }

    private MockHttpSession 게스트_세션(Guest guest) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionChatActorProvider.GUEST_ID_ATTRIBUTE, guest.getGuestId());
        return session;
    }

    private void 제한을_저장한다(Long userId, UUID guestId, Instant until) {
        jdbc.update("""
                INSERT INTO chat_input_guard_states (
                    user_id, guest_id, counting_from_at, restriction_started_at, restriction_until, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                """,
                userId,
                guestId,
                Timestamp.from(Instant.EPOCH),
                Timestamp.from(until.minusSeconds(60)),
                Timestamp.from(until),
                Timestamp.from(until.minusSeconds(60))
        );
    }

    private List<List<Map<String, Object>>> 저장_상태() {
        return List.of(
                jdbc.queryForList("SELECT * FROM chat_input_guard_states ORDER BY guard_state_id"),
                jdbc.queryForList("SELECT * FROM chat_input_guard_events ORDER BY guard_event_id"),
                jdbc.queryForList("SELECT * FROM chat_sessions ORDER BY session_id"),
                jdbc.queryForList("SELECT * FROM consult_requests ORDER BY consult_request_id"),
                jdbc.queryForList("SELECT * FROM consult_conditions ORDER BY condition_id"),
                jdbc.queryForList("SELECT * FROM chat_messages ORDER BY message_id"),
                jdbc.queryForList("SELECT * FROM chat_executions ORDER BY execution_id")
        );
    }

    private JsonNode 예시_본문(JsonNode examples, String name) throws Exception {
        JsonNode value = examples.path(name).path("value");
        return value.isTextual() ? mapper.readTree(value.asText()) : value;
    }
}
