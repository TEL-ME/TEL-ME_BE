package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.telme.chat.dto.req.ChatMessageSendRequest;
import com.telme.chat.dto.req.ChatSessionCreateRequest;
import com.telme.chat.dto.res.ChatMessageSendResponse;
import com.telme.chat.entity.ChatMessage;
import com.telme.chat.exception.ChatErrorCode;
import com.telme.global.common.exception.GeneralException;
import com.telme.member.entity.User;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
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

    @Autowired
    private ChatResponseTimer responseTimer;

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

        dispatched(sent, sessionId, "요금제 알려줘");
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(threadName.get())
                        .startsWith(ChatProcessingDispatcher.THREAD_NAME_PREFIX));
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
    @SuppressWarnings("unchecked")
    void failsExecutionWhenProcessorLookupFails() {
        Long sessionId = createSession();
        ChatMessageSendResponse sent = send(sessionId, "요금제 알려줘");
        ChatProcessingCommand command = dispatched(sent, sessionId, "요금제 알려줘");

        ObjectProvider<ChatProcessingPort> duplicated = mock(ObjectProvider.class);
        when(duplicated.getIfAvailable())
                .thenThrow(new NoUniqueBeanDefinitionException(ChatProcessingPort.class, 2, "처리 구현체 2개"));
        ChatProcessingDispatcher dispatcher = new ChatProcessingDispatcher(
                duplicated, chatExecutionService, responseTimer, transactionManager,
                new com.telme.chat.config.ChatProcessingProperties(4, 100, com.telme.chat.config.ChatProcessingProperties.DEFAULT_SHUTDOWN_WAIT));

        try {
            dispatcher.dispatch(command);

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                    assertThat(executionRow(sent.executionId()))
                            .containsEntry("status", "FAILED")
                            .containsEntry("error_code", ChatProcessingDispatcher.PROCESSING_ERROR));
        } finally {
            dispatcher.shutdown();
        }
    }

    @Test
    void failsExecutionRightAfterCommitWhenQueueIsFull() throws Exception {
        Long sessionId = createSession();
        ChatMessageSendResponse sent = send(sessionId, "요금제 알려줘");
        ChatProcessingCommand command = dispatched(sent, sessionId, "요금제 알려줘");

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
                chatProcessingPorts, chatExecutionService, responseTimer, transactionManager, saturated);

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
            // 거부된 실행도 접수부터 실패 기록까지의 시간을 남기고, 측정값 보관은 바로 정리한다
            assertThat(totalMs(sent.executionId())).isNotNull().isNotNegative();
            assertThat(responseTimer.findElapsedMillis(sent.executionId())).isNull();
            assertThat(send(sessionId, "다시 질문할게요").sequenceNo()).isEqualTo(3);
        } finally {
            release.countDown();
            saturated.shutdown();
        }
    }

    @Test
    void recordsTotalTimeFromReceiptUntilAnswerIsRecorded() {
        doAnswer(invocation -> {
            ChatProcessingCommand command = invocation.getArgument(0);
            chatExecutionService.completeAnswer(command.executionId(),
                    new ChatAnswer(ChatMessage.MessageType.ANSWER, "답변입니다.", null, null, null));
            return null;
        }).when(chatProcessingPort).request(any());

        Long sessionId = createSession();
        ChatMessageSendResponse sent = send(sessionId, "요금제 알려줘");

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(executionRow(sent.executionId())).containsEntry("status", "COMPLETED"));
        assertThat(totalMs(sent.executionId())).isNotNull().isNotNegative();
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(responseTimer.findElapsedMillis(sent.executionId())).isNull());
    }

    @Test
    void releasesMeasurementWhenProcessingThrows() {
        doThrow(new IllegalStateException("pipeline down")).when(chatProcessingPort).request(any());

        Long sessionId = createSession();
        ChatMessageSendResponse sent = send(sessionId, "요금제 알려줘");

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(executionRow(sent.executionId())).containsEntry("status", "FAILED"));
        assertThat(totalMs(sent.executionId())).isNotNull().isNotNegative();
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(responseTimer.findElapsedMillis(sent.executionId())).isNull());
    }

    @Test
    void keepsFirstFinisherWhenTimeoutAndProcessingFinishTogether() throws Exception {
        Long sessionId = createSession();
        ChatMessageSendResponse sent = send(sessionId, "요금제 알려줘");
        dispatched(sent, sessionId, "요금제 알려줘");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService finishers = Executors.newFixedThreadPool(2);
        try {
            Future<String> answer = finishers.submit(() -> finish(start, () -> chatExecutionService.completeAnswer(
                    sent.executionId(), new ChatAnswer(ChatMessage.MessageType.ANSWER, "답변입니다.", null, null, null))));
            Future<String> timeout = finishers.submit(() -> finish(start, () -> chatExecutionService.fail(
                    sent.executionId(), new ChatFailure(ChatMessage.Status.TIMEOUT, "EXECUTION_TIMEOUT"))));
            start.countDown();

            // 실행 행 잠금과 RUNNING 확인으로 한쪽만 끝내고, 다른 쪽은 기존 오류로 거부된다
            var outcomes = List.of(answer.get(10, TimeUnit.SECONDS), timeout.get(10, TimeUnit.SECONDS));
            assertThat(outcomes).containsOnlyOnce("DONE");
            assertThat(outcomes).containsOnlyOnce(ChatErrorCode.EXECUTION_NOT_RUNNING.getCode());
            String expected = "DONE".equals(outcomes.get(0)) ? "COMPLETED" : "FAILED";
            assertThat(executionRow(sent.executionId())).containsEntry("status", expected);
        } finally {
            finishers.shutdownNow();
        }
    }

    private String finish(CountDownLatch start, Runnable completion) throws InterruptedException {
        start.await();
        try {
            completion.run();
            return "DONE";
        } catch (GeneralException exception) {
            return exception.getErrorCode().getCode();
        }
    }

    private ChatProcessingCommand dispatched(ChatMessageSendResponse sent, Long sessionId, String content) {
        ArgumentCaptor<ChatProcessingCommand> captor = ArgumentCaptor.forClass(ChatProcessingCommand.class);
        verify(chatProcessingPort, timeout(5_000)).request(captor.capture());
        ChatProcessingCommand command = captor.getValue();
        assertThat(command.executionId()).isEqualTo(sent.executionId());
        assertThat(command.sessionId()).isEqualTo(sessionId);
        assertThat(command.inputMessageId()).isEqualTo(sent.messageId());
        assertThat(command.content()).isEqualTo(content);
        // 접수 요청에서 시작한 측정값이 비동기 처리까지 명령으로 전달된다
        assertThat(command.stopwatch()).isNotNull();
        // 처리 작업이 끝나면 실행별 측정값 보관을 정리한다
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(responseTimer.findElapsedMillis(sent.executionId())).isNull());
        return command;
    }

    private Integer totalMs(Long executionId) {
        return jdbcTemplate.queryForObject(
                "select total_ms from chat_executions where execution_id = ?", Integer.class, executionId);
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
