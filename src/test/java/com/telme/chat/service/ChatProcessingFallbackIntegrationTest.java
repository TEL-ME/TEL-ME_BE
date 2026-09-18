package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.telme.chat.dto.req.ChatMessageSendRequest;
import com.telme.chat.dto.req.ChatSessionCreateRequest;
import com.telme.chat.dto.res.ChatMessageSendResponse;
import com.telme.member.entity.User;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class ChatProcessingFallbackIntegrationTest {

    @Autowired
    private ChatSessionService chatSessionService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long userId;
    private ChatActor actor;

    @BeforeEach
    void setUpUser() {
        transactionTemplate.executeWithoutResult(status -> {
            User user = User.builder()
                    .email("fallback-" + UUID.randomUUID() + "@example.com")
                    .name("fallback")
                    .build();
            entityManager.persist(user);
            entityManager.flush();
            userId = user.getUserId();
        });
        actor = new ChatActor(userId, null);
    }

    @AfterEach
    void cleanUpUser() {
        await().atMost(Duration.ofSeconds(5)).until(() -> jdbcTemplate.queryForObject(
                "select count(*) from chat_executions e join chat_sessions s on s.session_id = e.session_id"
                        + " where s.user_id = ? and e.status = 'RUNNING'",
                Integer.class, userId) == 0);
        jdbcTemplate.update("delete from chat_sessions where user_id = ?", userId);
        jdbcTemplate.update("delete from users where user_id = ?", userId);
    }

    @Test
    void endsExecutionImmediatelyWhenNoProcessorIsRegisteredSoNextMessageIsAccepted() {
        Long sessionId = chatSessionService.createSession(actor, new ChatSessionCreateRequest("미연결 테스트"))
                .sessionId();
        ChatMessageSendResponse first = send(sessionId, "요금제 알려줘");

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(jdbcTemplate.queryForMap(
                        "select status, error_code from chat_executions where execution_id = ?",
                        first.executionId()))
                        .containsEntry("status", "FAILED")
                        .containsEntry("error_code", ChatProcessingDispatcher.NOT_CONNECTED));

        Map<String, Object> errorMessage = jdbcTemplate.queryForMap(
                "select role, message_type, status, reply_to_id from chat_messages"
                        + " where session_id = ? and sequence_no = 2",
                sessionId);
        assertThat(errorMessage)
                .containsEntry("role", "ASSISTANT")
                .containsEntry("message_type", "ERROR")
                .containsEntry("status", "FAILED")
                .containsEntry("reply_to_id", first.messageId());

        assertThat(send(sessionId, "다시 질문할게요").sequenceNo()).isEqualTo(3);
    }

    private ChatMessageSendResponse send(Long sessionId, String content) {
        return chatSessionService.sendMessage(actor, sessionId, new ChatMessageSendRequest(content));
    }
}
