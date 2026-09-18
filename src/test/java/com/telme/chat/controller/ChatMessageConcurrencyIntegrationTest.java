package com.telme.chat.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.entity.ChatMessage;
import com.telme.chat.service.ChatAnswer;
import com.telme.chat.service.ChatExecutionService;
import com.telme.chat.service.HttpSessionChatActorProvider;
import com.telme.member.entity.User;
import jakarta.persistence.EntityManager;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
class ChatMessageConcurrencyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ChatExecutionService chatExecutionService;

    private Long userId;

    @BeforeEach
    void setUpUser() {
        transactionTemplate.executeWithoutResult(status -> {
            User user = User.builder()
                    .email("concurrency-" + UUID.randomUUID() + "@example.com")
                    .name("concurrency")
                    .build();
            entityManager.persist(user);
            entityManager.flush();
            userId = user.getUserId();
        });
    }

    @AfterEach
    void cleanUpUser() {
        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.update("delete from chat_sessions where user_id = ?", userId);
            jdbcTemplate.update("delete from users where user_id = ?", userId);
        });
    }

    @Test
    void acceptsOnlyOneOfConcurrentMessages() throws Exception {
        long sessionId = createSession();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<MvcResult> first = executor.submit(() -> sendMessage(sessionId, "첫 번째 질문", ready, start));
            Future<MvcResult> second = executor.submit(() -> sendMessage(sessionId, "두 번째 질문", ready, start));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Integer> statuses = List.of(
                    first.get(10, TimeUnit.SECONDS).getResponse().getStatus(),
                    second.get(10, TimeUnit.SECONDS).getResponse().getStatus()
            );
            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
            assertThat(jdbcTemplate.queryForObject(
                    "select count(*) from chat_messages where session_id = ?", Integer.class, sessionId))
                    .isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void assignsDistinctSequenceNumbersWhenTimedOutExecutionFinishesLate() throws Exception {
        long sessionId = createSession();
        long executionId = objectMapper.readTree(postMessage(sessionId, "첫 질문").getResponse().getContentAsByteArray())
                .path("result").path("executionId").asLong();
        jdbcTemplate.update(
                "update chat_executions set started_at = now() - interval '1 hour' where execution_id = ?",
                executionId);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Integer> userMessage = executor.submit(() -> sequenceNo(sendMessage(sessionId, "추가 질문", ready, start)));
            Future<Integer> answer = executor.submit(() -> {
                ready.countDown();
                assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                return chatExecutionService.completeAnswer(executionId, new ChatAnswer(
                        ChatMessage.MessageType.ANSWER, "답변", null, null, null)).sequenceNo();
            });

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Integer> sequenceNumbers = List.of(
                    userMessage.get(10, TimeUnit.SECONDS),
                    answer.get(10, TimeUnit.SECONDS)
            );
            assertThat(sequenceNumbers).containsExactlyInAnyOrder(2, 3);
        } finally {
            executor.shutdownNow();
        }
    }

    private long createSession() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/chat/sessions")
                        .session(chatSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"동시성 테스트\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsByteArray())
                .path("result").path("sessionId").asLong();
    }

    private MvcResult sendMessage(
            long sessionId,
            String content,
            CountDownLatch ready,
            CountDownLatch start
    ) throws Exception {
        ready.countDown();
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        return postMessage(sessionId, content);
    }

    private MvcResult postMessage(long sessionId, String content) throws Exception {
        return mockMvc.perform(post("/api/v1/chat/sessions/{sessionId}/messages", sessionId)
                        .session(chatSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MessageRequest(content))))
                .andReturn();
    }

    private int sequenceNo(MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        return objectMapper.readTree(result.getResponse().getContentAsByteArray())
                .path("result").path("sequenceNo").asInt();
    }

    private MockHttpSession chatSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionChatActorProvider.USER_ID_ATTRIBUTE, userId);
        return session;
    }

    private record MessageRequest(String content) {
    }
}
