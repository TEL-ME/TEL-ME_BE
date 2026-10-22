package com.telme.chat.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.entity.ChatExecution;
import com.telme.chat.entity.ChatMessage;
import com.telme.chat.service.ChatExecutionRetryLookup;
import com.telme.chat.service.ChatExecutionService;
import com.telme.chat.service.ChatFailure;
import com.telme.chat.service.HttpSessionChatActorProvider;
import com.telme.member.entity.User;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ChatExecutionRetryApiIntegrationTest {

    private static final String RETRYABLE = "LLM502-4";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ChatExecutionService executions;

    @Autowired
    private ChatExecutionRetryLookup retryLookup;

    private MockHttpSession owner;
    private MockHttpSession other;

    @BeforeEach
    void setUpActors() {
        owner = sessionOf(persistUser("owner"));
        other = sessionOf(persistUser("other"));
    }

    @Test
    @DisplayName("재시도 가능 실패는 같은 질문으로 시도 2의 새 실행을 만들고, 질문 메시지는 새로 저장하지 않는다")
    void 재시도하면_같은_질문으로_새_실행이_만들어진다() throws Exception {
        long sessionId = createSession();
        JsonNode sent = send(sessionId, "해외 로밍 요금 알려줘");
        long failedId = sent.path("executionId").asLong();
        long questionId = sent.path("messageId").asLong();
        executions.fail(failedId, new ChatFailure(ChatMessage.Status.FAILED, RETRYABLE));

        history(sessionId).andExpectRetryable(failedId);
        MvcResult retried = mockMvc.perform(post(retryUrl(sessionId, failedId)).session(owner))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.result.messageId").value(questionId))
                .andExpect(jsonPath("$.result.executionStatus").value("RUNNING"))
                .andReturn();

        long retriedId = body(retried).path("result").path("executionId").asLong();
        assertThat(retriedId).isNotEqualTo(failedId);
        entityManager.flush();
        entityManager.clear();
        ChatExecution created = entityManager.find(ChatExecution.class, retriedId);
        assertThat(created.getAttempt()).isEqualTo(2);
        assertThat(created.getInputMessage().getMessageId()).isEqualTo(questionId);
        assertThat(entityManager.createQuery(
                        "select count(m) from ChatMessage m where m.session.sessionId = :id and m.role = 'USER'",
                        Long.class).setParameter("id", sessionId).getSingleResult()).isEqualTo(1L);
        history(sessionId).andExpectRetryable(null);
        // 대체 게이트가 보는 값: 처음 실행은 아니고, 다시 시도한 실행만 true
        assertThat(retryLookup.afterUserRetry(failedId)).isFalse();
        assertThat(retryLookup.afterUserRetry(retriedId)).isTrue();
    }

    @Test
    @DisplayName("질문마다 다시 시도는 한 번만 가능하다")
    void 두_번째_재시도는_거절한다() throws Exception {
        long sessionId = createSession();
        long failedId = send(sessionId, "질문").path("executionId").asLong();
        executions.fail(failedId, new ChatFailure(ChatMessage.Status.FAILED, RETRYABLE));
        mockMvc.perform(post(retryUrl(sessionId, failedId)).session(owner)).andExpect(status().isCreated());

        mockMvc.perform(post(retryUrl(sessionId, failedId)).session(owner))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHAT409-6"));
    }

    @Test
    @DisplayName("재시도 가능 실패가 아니거나 완료된 실행은 다시 시도할 수 없다")
    void 재시도_가능_실패만_다시_시도한다() throws Exception {
        long sessionId = createSession();
        long failedId = send(sessionId, "질문").path("executionId").asLong();
        executions.fail(failedId, new ChatFailure(ChatMessage.Status.FAILED, "LLM502-0"));

        mockMvc.perform(post(retryUrl(sessionId, failedId)).session(owner))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHAT409-5"));
        history(sessionId).andExpectRetryable(null);
    }

    @Test
    @DisplayName("실패 뒤 다른 질문이 이어졌으면 지나간 답변은 다시 시도할 수 없다")
    void 최신_실행이_아니면_거절한다() throws Exception {
        long sessionId = createSession();
        long failedId = send(sessionId, "첫 질문").path("executionId").asLong();
        executions.fail(failedId, new ChatFailure(ChatMessage.Status.FAILED, RETRYABLE));
        long nextId = send(sessionId, "다음 질문").path("executionId").asLong();
        executions.completeWithoutOutput(nextId);

        mockMvc.perform(post(retryUrl(sessionId, failedId)).session(owner))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHAT409-5"));
    }

    @Test
    @DisplayName("다른 사용자의 세션이나 다른 세션의 실행은 찾을 수 없다")
    void 소유자만_다시_시도한다() throws Exception {
        long sessionId = createSession();
        long otherSessionId = createSession();
        long failedId = send(sessionId, "질문").path("executionId").asLong();
        executions.fail(failedId, new ChatFailure(ChatMessage.Status.FAILED, RETRYABLE));

        mockMvc.perform(post(retryUrl(sessionId, failedId)).session(other))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHAT404-0"));
        mockMvc.perform(post(retryUrl(otherSessionId, failedId)).session(owner))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHAT404-1"));
    }

    private String retryUrl(long sessionId, long executionId) {
        return "/api/v1/chat/sessions/" + sessionId + "/executions/" + executionId + "/retry";
    }

    private long createSession() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/chat/sessions")
                        .session(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"재시도\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return body(result).path("result").path("sessionId").asLong();
    }

    private JsonNode send(long sessionId, String content) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/chat/sessions/{sessionId}/messages", sessionId)
                        .session(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("content", content))))
                .andExpect(status().isCreated())
                .andReturn();
        return body(result).path("result");
    }

    private HistoryResult history(long sessionId) throws Exception {
        return new HistoryResult(body(mockMvc.perform(
                        get("/api/v1/chat/sessions/{sessionId}/messages", sessionId).session(owner))
                .andExpect(status().isOk())
                .andReturn()));
    }

    private record HistoryResult(JsonNode body) {
        void andExpectRetryable(Long executionId) {
            JsonNode value = body.path("result").path("retryableExecutionId");
            if (executionId == null) {
                assertThat(value.isNull() || value.isMissingNode()).isTrue();
            } else {
                assertThat(value.asLong()).isEqualTo(executionId);
            }
        }
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private User persistUser(String prefix) {
        User user = User.builder()
                .email(prefix + "-" + UUID.randomUUID() + "@example.com")
                .name(prefix)
                .build();
        entityManager.persist(user);
        entityManager.flush();
        return user;
    }

    private MockHttpSession sessionOf(User user) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionChatActorProvider.USER_ID_ATTRIBUTE, user.getUserId());
        return session;
    }
}
