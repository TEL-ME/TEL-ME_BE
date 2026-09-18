package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.telme.chat.dto.req.ChatMessageSendRequest;
import com.telme.chat.dto.req.ChatSessionCreateRequest;
import com.telme.chat.dto.res.ChatMessageSendResponse;
import com.telme.member.entity.User;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class ChatProcessingDispatchIntegrationTest {

    @Autowired
    private ChatSessionService chatSessionService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ChatExecutionService chatExecutionService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ObjectProvider<ChatProcessingPort> chatProcessingPorts;

    @MockitoBean
    private ChatProcessingPort chatProcessingPort;

    private Long userId;
    private ChatActor actor;

    @BeforeEach
    void setUpUser() {
        transactionTemplate.executeWithoutResult(status -> {
            User user = User.builder()
                    .email("dispatch-" + UUID.randomUUID() + "@example.com")
                    .name("dispatch")
                    .build();
            entityManager.persist(user);
            entityManager.flush();
            userId = user.getUserId();
        });
        actor = new ChatActor(userId, null);
    }

    @AfterEach
    void cleanUpUser() {
        jdbcTemplate.update("delete from chat_sessions where user_id = ?", userId);
        jdbcTemplate.update("delete from users where user_id = ?", userId);
    }

    @Test
    void dispatchesCommittedMessageOffRequestThread() {
        AtomicReference<String> threadName = new AtomicReference<>();
        doAnswer(invocation -> {
            threadName.set(Thread.currentThread().getName());
            return null;
        }).when(chatProcessingPort).request(any());

        Long sessionId = createSession();
        ChatMessageSendResponse sent = send(sessionId, "요금제 알려줘");

        verify(chatProcessingPort, timeout(5_000)).request(
                new ChatProcessingCommand(sent.executionId(), sessionId, sent.messageId()));
        assertThat(threadName.get()).startsWith(ChatProcessingDispatcher.THREAD_NAME_PREFIX);
    }

    @Test
    void doesNotDispatchRolledBackMessage() {
        Long sessionId = createSession();

        transactionTemplate.executeWithoutResult(status -> {
            send(sessionId, "되돌려질 질문");
            status.setRollbackOnly();
        });

        verify(chatProcessingPort, after(1_000).times(0)).request(any());
    }

    @Test
    void failsExecutionWhenProcessingThrows() {
        doThrow(new IllegalStateException("pipeline down")).when(chatProcessingPort).request(any());

        Long sessionId = createSession();
        ChatMessageSendResponse sent = send(sessionId, "요금제 알려줘");

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(executionRow(sent.executionId()))
                        .containsEntry("status", "FAILED")
                        .containsEntry("error_code", ChatProcessingDispatcher.PROCESSING_ERROR));
    }

    @Test
    void failsExecutionRightAfterCommitWhenQueueIsFull() throws Exception {
        Long sessionId = createSession();
        ChatMessageSendResponse sent = send(sessionId, "요금제 알려줘");
        ChatProcessingCommand command = new ChatProcessingCommand(sent.executionId(), sessionId, sent.messageId());
        verify(chatProcessingPort, timeout(5_000)).request(command);

        ThreadPoolTaskExecutor saturated = new ThreadPoolTaskExecutor();
        saturated.setCorePoolSize(1);
        saturated.setMaxPoolSize(1);
        saturated.setQueueCapacity(0);
        saturated.initialize();
        CountDownLatch release = new CountDownLatch(1);
        saturated.execute(() -> {
            try {
                release.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        });
        ChatProcessingDispatcher dispatcher = new ChatProcessingDispatcher(
                chatProcessingPorts, chatExecutionService, transactionManager, saturated);

        try {
            transactionTemplate.executeWithoutResult(status ->
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            dispatcher.dispatch(command);
                        }
                    }));

            assertThat(jdbcTemplate.queryForMap(
                    "select e.status, e.error_code, m.status as message_status, m.completed_at"
                            + " from chat_executions e join chat_messages m on m.message_id = e.output_message_id"
                            + " where e.execution_id = ?",
                    sent.executionId()))
                    .containsEntry("status", "FAILED")
                    .containsEntry("error_code", ChatProcessingDispatcher.DISPATCH_REJECTED)
                    .containsEntry("message_status", "FAILED")
                    .extractingByKey("completed_at").isNotNull();
            assertThat(send(sessionId, "다시 질문할게요").sequenceNo()).isEqualTo(3);
        } finally {
            release.countDown();
            saturated.shutdown();
        }
    }

    private Long createSession() {
        return chatSessionService.createSession(actor, new ChatSessionCreateRequest("요청 연동 테스트")).sessionId();
    }

    private ChatMessageSendResponse send(Long sessionId, String content) {
        return chatSessionService.sendMessage(actor, sessionId, new ChatMessageSendRequest(content));
    }

    private Map<String, Object> executionRow(Long executionId) {
        return jdbcTemplate.queryForMap(
                "select status, error_code from chat_executions where execution_id = ?", executionId);
    }
}
