package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.telme.chat.dto.req.ChatMessageSendRequest;
import com.telme.chat.dto.req.ChatSessionCreateRequest;
import com.telme.chat.dto.res.ChatMessageSendResponse;
import com.telme.chat.entity.ChatMessage;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.service.LlmClient;
import com.telme.member.entity.User;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class ChatSummaryFlowIntegrationTest {

    @Autowired
    private ChatSessionService chatSessionService;

    @Autowired
    private ChatExecutionService chatExecutionService;

    @Autowired
    private ChatContextBuilder chatContextBuilder;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private ChatProcessingPort chatProcessingPort;

    @MockitoBean
    private LlmClient llmClient;

    private Long userId;
    private ChatActor actor;
    private Long sessionId;
    private CountDownLatch releaseSummary;

    @BeforeEach
    void setUp() {
        when(llmClient.generate(any(LlmRequest.class))).thenReturn("[FAKE] 테스트용 응답입니다.");
        transactionTemplate.executeWithoutResult(status -> {
            User user = User.builder()
                    .email("summary-flow-" + UUID.randomUUID() + "@example.com")
                    .name("summary-flow")
                    .build();
            entityManager.persist(user);
            entityManager.flush();
            userId = user.getUserId();
        });
        actor = new ChatActor(userId, null);
        sessionId = chatSessionService.createSession(actor, new ChatSessionCreateRequest("요약 흐름 테스트"))
                .sessionId();
    }

    @AfterEach
    void cleanUp() {
        if (releaseSummary != null) {
            releaseSummary.countDown();
        }
        jdbcTemplate.update("delete from chat_sessions where session_id = ?", sessionId);
        jdbcTemplate.update("delete from users where user_id = ?", userId);
    }

    @Test
    void incrementallySummarizesOldConversationAndUsesOnlyRecentMessagesInNextContext() {
        for (int turn = 1; turn <= 12; turn++) {
            ChatMessageSendResponse question = chatSessionService.sendMessage(
                    actor, sessionId, new ChatMessageSendRequest("질문 " + turn));
            chatExecutionService.completeAnswer(question.executionId(), new ChatAnswer(
                    ChatMessage.MessageType.ANSWER,
                    "답변 " + turn,
                    ChatMessage.AnswerBasis.GROUNDED,
                    List.of(),
                    null
            ));
        }

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(summaryState())
                        .containsEntry("summary", "[FAKE] 테스트용 응답입니다.")
                        .containsEntry("summary_through_sequence_no", 16));

        ChatMessageSendResponse current = chatSessionService.sendMessage(
                actor, sessionId, new ChatMessageSendRequest("현재 질문"));
        ChatContext context = chatContextBuilder.build(new ChatProcessingCommand(
                current.executionId(), sessionId, current.messageId(), "현재 질문"), 4_096);

        assertThat(context.summary()).isEqualTo("[FAKE] 테스트용 응답입니다.");
        assertThat(context.history()).hasSize(8);
        assertThat(context.history())
                .extracting(ChatContextMessage::sequenceNo)
                .containsExactly(17, 18, 19, 20, 21, 22, 23, 24);
    }

    @Test
    void keepsOneTurnBufferWhileAsynchronousSummaryIsStillRunning() throws InterruptedException {
        CountDownLatch summaryStarted = new CountDownLatch(1);
        releaseSummary = new CountDownLatch(1);
        when(llmClient.generate(any(LlmRequest.class))).thenAnswer(invocation -> {
            summaryStarted.countDown();
            if (!releaseSummary.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("요약 테스트 대기 시간 초과");
            }
            return "지연된 요약";
        });

        for (int turn = 1; turn <= 8; turn++) {
            ChatMessageSendResponse question = chatSessionService.sendMessage(
                    actor, sessionId, new ChatMessageSendRequest("질문 " + turn));
            chatExecutionService.completeAnswer(question.executionId(), new ChatAnswer(
                    ChatMessage.MessageType.ANSWER,
                    "답변 " + turn,
                    ChatMessage.AnswerBasis.GROUNDED,
                    List.of(),
                    null
            ));
        }
        assertThat(summaryStarted.await(5, TimeUnit.SECONDS)).isTrue();

        ChatMessageSendResponse current = chatSessionService.sendMessage(
                actor, sessionId, new ChatMessageSendRequest("요약 완료 전 질문"));
        ChatContext context = chatContextBuilder.build(new ChatProcessingCommand(
                current.executionId(), sessionId, current.messageId(), "요약 완료 전 질문"), 4_096);

        assertThat(context.summary()).isNull();
        assertThat(context.history())
                .extracting(ChatContextMessage::sequenceNo)
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16);

        releaseSummary.countDown();
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(summaryState()).containsEntry("summary_through_sequence_no", 8));
    }

    @Test
    void retriesFailedSummaryOnNextCompletedAnswer() throws InterruptedException {
        CountDownLatch firstFailure = new CountDownLatch(1);
        AtomicInteger attempts = new AtomicInteger();
        when(llmClient.generate(any(LlmRequest.class))).thenAnswer(invocation -> {
            if (attempts.incrementAndGet() == 1) {
                firstFailure.countDown();
                throw new IllegalStateException("일시적인 요약 실패");
            }
            return "재시도된 요약";
        });

        for (int turn = 1; turn <= 8; turn++) {
            completeTurn(turn);
        }
        assertThat(firstFailure.await(5, TimeUnit.SECONDS)).isTrue();

        completeTurn(9);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(summaryState())
                        .containsEntry("summary", "재시도된 요약")
                        .containsEntry("summary_through_sequence_no", 10));
        assertThat(attempts).hasValue(2);
    }

    private void completeTurn(int turn) {
        ChatMessageSendResponse question = chatSessionService.sendMessage(
                actor, sessionId, new ChatMessageSendRequest("질문 " + turn));
        chatExecutionService.completeAnswer(question.executionId(), new ChatAnswer(
                ChatMessage.MessageType.ANSWER,
                "답변 " + turn,
                ChatMessage.AnswerBasis.GROUNDED,
                List.of(),
                null
        ));
    }

    private java.util.Map<String, Object> summaryState() {
        return jdbcTemplate.queryForMap(
                "select summary, summary_through_sequence_no from chat_sessions where session_id = ?",
                sessionId
        );
    }
}
