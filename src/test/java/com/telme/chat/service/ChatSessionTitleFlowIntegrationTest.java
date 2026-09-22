package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.telme.chat.entity.ChatExecution;
import com.telme.chat.entity.ChatMessage;
import com.telme.chat.entity.ChatSession;
import com.telme.member.entity.User;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class ChatSessionTitleFlowIntegrationTest {

    @Autowired
    private ChatExecutionService chatExecutionService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private Long userId;
    private Long sessionId;

    @AfterEach
    void cleanUp() {
        if (sessionId != null) {
            jdbcTemplate.update("delete from chat_sessions where session_id = ?", sessionId);
        }
        if (userId != null) {
            jdbcTemplate.update("delete from users where user_id = ?", userId);
        }
    }

    @Test
    void generatesTitleAfterFirstExecutionCommits() {
        TestExecution testExecution = transactionTemplate.execute(status -> createRunningExecution());

        chatExecutionService.completeAnswer(testExecution.executionId(), new ChatAnswer(
                ChatMessage.MessageType.ANSWER,
                "가족 결합 상품을 안내해 드릴게요.",
                ChatMessage.AnswerBasis.GROUNDED,
                null,
                null
        ));

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(findTitle(testExecution.sessionId())).isEqualTo("[FAKE] 테스트용 응답입니다."));
    }

    private TestExecution createRunningExecution() {
        User user = User.builder()
                .email("title-flow-" + UUID.randomUUID() + "@example.com")
                .name("title-flow")
                .build();
        entityManager.persist(user);

        ChatSession session = ChatSession.builder()
                .userId(user.getUserId())
                .build();
        entityManager.persist(session);

        ChatMessage question = ChatMessage.builder()
                .session(session)
                .sequenceNo(1)
                .role(ChatMessage.Role.USER)
                .messageType(ChatMessage.MessageType.QUESTION)
                .content("가족 결합 요금제를 알려줘")
                .status(ChatMessage.Status.COMPLETED)
                .completedAt(Instant.now())
                .build();
        entityManager.persist(question);

        ChatExecution execution = ChatExecution.builder()
                .session(session)
                .inputMessage(question)
                .status(ChatExecution.Status.RUNNING)
                .build();
        entityManager.persist(execution);
        entityManager.flush();
        userId = user.getUserId();
        sessionId = session.getSessionId();
        return new TestExecution(session.getSessionId(), execution.getExecutionId());
    }

    private String findTitle(Long sessionId) {
        return jdbcTemplate.queryForObject(
                "select title from chat_sessions where session_id = ?",
                String.class,
                sessionId
        );
    }

    private record TestExecution(Long sessionId, Long executionId) {
    }
}
