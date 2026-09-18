package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.telme.chat.config.ChatExecutionProperties;
import com.telme.chat.dto.req.ChatMessageSendRequest;
import com.telme.chat.dto.req.ChatSessionCreateRequest;
import com.telme.chat.dto.res.ChatMessageSendResponse;
import com.telme.chat.entity.ChatExecution;
import com.telme.chat.entity.ChatMessage;
import com.telme.chat.repository.ChatExecutionRepository;
import com.telme.member.entity.User;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ChatExecutionTimeoutSchedulerIntegrationTest {

    @Autowired
    private ChatSessionService chatSessionService;

    @Autowired
    private ChatExecutionService chatExecutionService;

    @Autowired
    private ChatExecutionRepository chatExecutionRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ChatExecutionProperties chatExecutionProperties;

    @Autowired
    private ApplicationContext applicationContext;

    private ChatExecutionTimeoutScheduler scheduler;
    private ChatActor actor;

    @BeforeEach
    void setUp() {
        scheduler = new ChatExecutionTimeoutScheduler(
                chatExecutionRepository,
                chatExecutionService,
                chatExecutionProperties
        );

        User user = User.builder()
                .email("timeout-" + UUID.randomUUID() + "@example.com")
                .name("timeout")
                .build();
        entityManager.persist(user);
        entityManager.flush();
        actor = new ChatActor(user.getUserId(), null);
    }

    @Test
    void usesDefaultTimeoutAndDisablesBackgroundSchedulerInTests() {
        assertThat(chatExecutionProperties.runningTimeout()).isEqualTo(Duration.ofMinutes(5));
        assertThat(applicationContext.getBeansOfType(ChatExecutionTimeoutScheduler.class)).isEmpty();
    }

    @Test
    void timesOutOnlyStaleRunningExecutions() {
        Long staleSessionId = createSession();
        ChatMessageSendResponse stale = send(staleSessionId, "멈춘 질문");
        Long recentSessionId = createSession();
        ChatMessageSendResponse recent = send(recentSessionId, "방금 보낸 질문");
        backdate(stale.executionId());

        scheduler.timeOutExecutionsStartedBefore(Instant.now().minus(Duration.ofMinutes(5)));

        entityManager.flush();
        entityManager.clear();
        ChatExecution timedOut = entityManager.find(ChatExecution.class, stale.executionId());
        assertThat(timedOut.getStatus()).isEqualTo(ChatExecution.Status.FAILED);
        assertThat(timedOut.getErrorCode()).isEqualTo(ChatExecutionTimeoutScheduler.ERROR_CODE);
        assertThat(timedOut.getOutputMessage().getMessageType()).isEqualTo(ChatMessage.MessageType.ERROR);
        assertThat(timedOut.getOutputMessage().getStatus()).isEqualTo(ChatMessage.Status.TIMEOUT);
        assertThat(timedOut.getOutputMessage().getCompletedAt()).isEqualTo(timedOut.getEndedAt());

        assertThat(entityManager.find(ChatExecution.class, recent.executionId()).getStatus())
                .isEqualTo(ChatExecution.Status.RUNNING);

        assertThat(send(staleSessionId, "다시 질문할게요").sequenceNo()).isEqualTo(3);
    }

    @Test
    void findsOnlyStaleRunningExecutionsAfterGivenId() {
        ChatMessageSendResponse first = send(createSession(), "첫 번째 멈춘 질문");
        ChatMessageSendResponse second = send(createSession(), "두 번째 멈춘 질문");
        backdate(first.executionId());
        backdate(second.executionId());
        Instant startedBefore = Instant.now().minus(Duration.ofMinutes(5));

        assertThat(chatExecutionRepository.findExecutionIdsStartedBefore(
                ChatExecution.Status.RUNNING, startedBefore, first.executionId() - 1, PageRequest.of(0, 10)))
                .containsExactly(first.executionId(), second.executionId());
        assertThat(chatExecutionRepository.findExecutionIdsStartedBefore(
                ChatExecution.Status.RUNNING, startedBefore, first.executionId(), PageRequest.of(0, 10)))
                .containsExactly(second.executionId());
    }

    private Long createSession() {
        return chatSessionService.createSession(actor, new ChatSessionCreateRequest("타임아웃 테스트")).sessionId();
    }

    private ChatMessageSendResponse send(Long sessionId, String content) {
        return chatSessionService.sendMessage(actor, sessionId, new ChatMessageSendRequest(content));
    }

    private void backdate(Long executionId) {
        entityManager.flush();
        entityManager.createNativeQuery(
                        "update chat_executions set started_at = now() - interval '1 hour' where execution_id = ?")
                .setParameter(1, executionId)
                .executeUpdate();
        entityManager.clear();
    }
}
