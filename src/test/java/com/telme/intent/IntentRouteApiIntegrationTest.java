package com.telme.intent;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.telme.chat.entity.ChatMessage;
import com.telme.chat.entity.ChatSession;
import com.telme.chat.service.HttpSessionChatActorProvider;
import com.telme.llm.service.LlmClient;
import com.telme.member.entity.Guest;
import com.telme.member.entity.User;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
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
import org.springframework.transaction.support.TransactionTemplate;

// @Transactional 없이 실행하여 트랜잭션 밖 컨트롤러 지연 로딩(LazyInitializationException) 회귀를 검증한다
@SpringBootTest
@AutoConfigureMockMvc
class IntentRouteApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private LlmClient llmClient;

    @AfterEach
    void tearDown() {
        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.update("delete from consult_conditions");
            jdbcTemplate.update("delete from consult_requests");
            jdbcTemplate.update("delete from query_routings");
            jdbcTemplate.update("delete from chat_messages");
            jdbcTemplate.update("delete from chat_sessions");
            jdbcTemplate.update("delete from guests");
            jdbcTemplate.update("delete from users where email like '%routeapi-%'");
        });
    }

    private User persistUser(String prefix) {
        return transactionTemplate.execute(status -> {
            User user = User.builder()
                .email(prefix + "-" + UUID.randomUUID() + "@example.com")
                .name(prefix)
                .build();
            entityManager.persist(user);
            entityManager.flush();
            return user;
        });
    }

    private UUID persistGuest() {
        return transactionTemplate.execute(status -> {
            UUID guestId = UUID.randomUUID();
            Guest guest = Guest.builder()
                .guestId(guestId)
                .lastSeenAt(Instant.now())
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .build();
            entityManager.persist(guest);
            entityManager.flush();
            return guestId;
        });
    }

    private ChatMessage persistMessage(ChatSession session, String content) {
        return transactionTemplate.execute(status -> {
            entityManager.persist(session);
            ChatMessage message = ChatMessage.builder()
                .session(session)
                .sequenceNo(1)
                .role(ChatMessage.Role.USER)
                .messageType(ChatMessage.MessageType.QUESTION)
                .content(content)
                .status(ChatMessage.Status.COMPLETED)
                .build();
            entityManager.persist(message);
            entityManager.flush();
            return message;
        });
    }

    @Test
    @DisplayName("MockMvc HTTP 라우팅: 멤버 소유자 정상 요청 시 200 OK 반환 (LazyInitializationException 방어 검증)")
    void routeApi_memberOwner_returns200() throws Exception {
        User user = persistUser("routeapi-owner");
        ChatSession session = ChatSession.builder()
            .userId(user.getUserId())
            .title("회원 세션")
            .status(ChatSession.Status.ACTIVE)
            .build();
        ChatMessage message = persistMessage(session, "5G 요금제 알려줘");

        given(llmClient.generate(any())).willReturn("""
            {"intent":"FAQ","confidence":0.95,"refinedQuery":"5G 요금제 안내",
             "extractedConditions":{},"subQueries":[{"order":1,"intent":"FAQ","queryText":"5G 요금제 안내"}]}
            """);

        MockHttpSession mockSession = new MockHttpSession();
        mockSession.setAttribute(HttpSessionChatActorProvider.USER_ID_ATTRIBUTE, user.getUserId());

        mockMvc.perform(post("/api/v1/intent-routes")
                .session(mockSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"messageId": %d, "content": "5G 요금제 알려줘"}
                    """.formatted(message.getMessageId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.intent").value("FAQ"));
    }

    @Test
    @DisplayName("MockMvc HTTP 라우팅: 게스트 소유자 정상 요청 시 200 OK 반환 (LazyInitializationException 방어 검증)")
    void routeApi_guestOwner_returns200() throws Exception {
        UUID guestId = persistGuest();
        ChatSession session = ChatSession.builder()
            .guestId(guestId)
            .userId(null)
            .title("게스트 세션")
            .status(ChatSession.Status.ACTIVE)
            .build();
        ChatMessage message = persistMessage(session, "강남역 매장 찾아줘");

        given(llmClient.generate(any())).willReturn("""
            {"intent":"STORE","confidence":0.95,"refinedQuery":"강남역 매장 안내",
             "extractedConditions":{},"subQueries":[{"order":1,"intent":"STORE","queryText":"강남역 매장 안내"}]}
            """);

        MockHttpSession mockSession = new MockHttpSession();
        mockSession.setAttribute(HttpSessionChatActorProvider.GUEST_ID_ATTRIBUTE, guestId);

        mockMvc.perform(post("/api/v1/intent-routes")
                .session(mockSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"messageId": %d, "content": "강남역 매장 찾아줘"}
                    """.formatted(message.getMessageId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.intent").value("STORE"));
    }

    @Test
    @DisplayName("MockMvc HTTP 라우팅: 타인의 세션 메시지 요청 시 403 Forbidden 반환")
    void routeApi_forbiddenWhenNotOwner() throws Exception {
        User owner = persistUser("routeapi-owner");
        User hacker = persistUser("routeapi-hacker");

        ChatSession session = ChatSession.builder()
            .userId(owner.getUserId())
            .title("소유자 세션")
            .status(ChatSession.Status.ACTIVE)
            .build();
        ChatMessage message = persistMessage(session, "비밀 질문");

        MockHttpSession hackerSession = new MockHttpSession();
        hackerSession.setAttribute(HttpSessionChatActorProvider.USER_ID_ATTRIBUTE, hacker.getUserId());

        mockMvc.perform(post("/api/v1/intent-routes")
                .session(hackerSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"messageId": %d, "content": "비밀 질문"}
                    """.formatted(message.getMessageId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON403-0"));
    }

    @Test
    @DisplayName("MockMvc HTTP 라우팅: 세션 없이 요청 시 401 Unauthorized 반환")
    void routeApi_unauthenticatedWhenNoSession() throws Exception {
        mockMvc.perform(post("/api/v1/intent-routes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"messageId\": 1, \"content\": \"질문\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("CHAT401-0"));
    }
}
