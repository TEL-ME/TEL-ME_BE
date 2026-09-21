package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.telme.chat.config.ChatSummaryProperties;
import com.telme.chat.entity.ChatExecution;
import com.telme.chat.entity.ChatMessage;
import com.telme.chat.entity.ChatSession;
import com.telme.chat.repository.ChatExecutionRepository;
import com.telme.chat.repository.ChatMessageRepository;
import com.telme.chat.repository.ChatSessionRepository;
import com.telme.member.entity.User;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ChatSummaryStoreIntegrationTest {

    @Autowired
    private ChatSummaryStore chatSummaryStore;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private ChatExecutionRepository chatExecutionRepository;

    @Autowired
    private ChatTokenEstimator tokenEstimator;

    @Test
    void startsAtExactMessageThresholdAndKeepsConfiguredRecentMessages() {
        ChatSession session = createSession();
        ChatMessage lastQuestion = null;
        ChatMessage lastAnswer = null;
        int sequenceNo = 1;
        for (int turn = 1; turn <= 8; turn++) {
            lastQuestion = message(
                    session, sequenceNo++, ChatMessage.Role.USER, ChatMessage.MessageType.QUESTION,
                    "질문 " + turn, null);
            lastAnswer = message(
                    session, sequenceNo++, ChatMessage.Role.ASSISTANT, ChatMessage.MessageType.ANSWER,
                    "답변 " + turn, lastQuestion);
        }
        ChatExecution execution = execution(session, lastQuestion, lastAnswer);
        entityManager.flush();
        entityManager.clear();

        ChatSummarySnapshot snapshot = chatSummaryStore.prepare(
                new ChatSummaryRequested(execution.getExecutionId(), session.getSessionId(), 16)).orElseThrow();

        assertThat(snapshot.throughSequenceNo()).isEqualTo(8);
        assertThat(snapshot.messages())
                .extracting(ChatContextMessage::sequenceNo)
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8);
    }

    @Test
    void doesNotStartBelowMessageAndTokenThresholds() {
        ChatSession session = createSession();
        ChatMessage lastQuestion = null;
        ChatMessage lastAnswer = null;
        int sequenceNo = 1;
        for (int turn = 1; turn <= 7; turn++) {
            lastQuestion = message(
                    session, sequenceNo++, ChatMessage.Role.USER, ChatMessage.MessageType.QUESTION,
                    "q" + turn, null);
            lastAnswer = message(
                    session, sequenceNo++, ChatMessage.Role.ASSISTANT, ChatMessage.MessageType.ANSWER,
                    "a" + turn, lastQuestion);
        }
        ChatExecution execution = execution(session, lastQuestion, lastAnswer);
        entityManager.flush();
        entityManager.clear();

        assertThat(chatSummaryStore.prepare(
                new ChatSummaryRequested(execution.getExecutionId(), session.getSessionId(), 14))).isEmpty();
    }

    @Test
    void preparesOldestCompleteExchangeAndUpdatesCursorAtomically() {
        ChatSession session = createSession();
        ChatMessage lastQuestion = null;
        ChatMessage lastAnswer = null;
        int sequenceNo = 1;
        for (int turn = 1; turn <= 9; turn++) {
            lastQuestion = message(
                    session, sequenceNo++, ChatMessage.Role.USER, ChatMessage.MessageType.QUESTION,
                    "질문 " + turn, null);
            lastAnswer = message(
                    session, sequenceNo++, ChatMessage.Role.ASSISTANT, ChatMessage.MessageType.ANSWER,
                    "답변 " + turn, lastQuestion);
        }
        ChatExecution execution = execution(session, lastQuestion, lastAnswer);
        entityManager.flush();
        entityManager.clear();

        Optional<ChatSummarySnapshot> prepared = chatSummaryStore.prepare(
                new ChatSummaryRequested(execution.getExecutionId(), session.getSessionId(), 18));

        assertThat(prepared).isPresent();
        ChatSummarySnapshot snapshot = prepared.orElseThrow();
        assertThat(snapshot.expectedSequenceNo()).isZero();
        assertThat(snapshot.throughSequenceNo()).isEqualTo(10);
        assertThat(snapshot.messages())
                .extracting(ChatContextMessage::sequenceNo)
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

        assertThat(chatSummaryStore.saveIfCurrent(snapshot, "첫 번째 상담 요약")).isTrue();
        assertThat(chatSummaryStore.saveIfCurrent(snapshot, "늦게 끝난 상담 요약")).isFalse();

        entityManager.clear();
        ChatSession updated = entityManager.find(ChatSession.class, session.getSessionId());
        assertThat(updated.getSummary()).isEqualTo("첫 번째 상담 요약");
        assertThat(updated.getSummaryThroughSequenceNo()).isEqualTo(10);
    }

    @Test
    void summarizesOldMessagesWhenTokenBudgetOverflowsBeforeMessageLimit() {
        ChatSession session = createSession();
        ChatMessage lastQuestion = null;
        ChatMessage lastAnswer = null;
        int sequenceNo = 1;
        for (int turn = 1; turn <= 3; turn++) {
            lastQuestion = message(
                    session, sequenceNo++, ChatMessage.Role.USER, ChatMessage.MessageType.QUESTION,
                    "질문 " + turn, null);
            lastAnswer = message(
                    session, sequenceNo++, ChatMessage.Role.ASSISTANT, ChatMessage.MessageType.ANSWER,
                    "답변 " + turn, lastQuestion);
        }
        ChatExecution execution = execution(session, lastQuestion, lastAnswer);
        entityManager.flush();
        entityManager.clear();

        ChatSummaryStore tokenLimitedStore = new ChatSummaryStore(
                chatSessionRepository,
                chatMessageRepository,
                chatExecutionRepository,
                new ChatSummaryProperties(16, 16, 8, 15, 16, 3_072, 512),
                tokenEstimator
        );

        ChatSummarySnapshot snapshot = tokenLimitedStore.prepare(
                new ChatSummaryRequested(execution.getExecutionId(), session.getSessionId(), 6)).orElseThrow();

        assertThat(snapshot.throughSequenceNo()).isEqualTo(4);
        assertThat(snapshot.messages())
                .extracting(ChatContextMessage::sequenceNo)
                .containsExactly(1, 2, 3, 4);
    }

    private ChatSession createSession() {
        User user = User.builder()
                .email("summary-" + UUID.randomUUID() + "@example.com")
                .name("summary")
                .build();
        entityManager.persist(user);
        ChatSession session = ChatSession.builder().userId(user.getUserId()).build();
        entityManager.persist(session);
        entityManager.flush();
        return session;
    }

    private ChatMessage message(
            ChatSession session,
            int sequenceNo,
            ChatMessage.Role role,
            ChatMessage.MessageType type,
            String content,
            ChatMessage replyTo
    ) {
        ChatMessage message = ChatMessage.builder()
                .session(session)
                .sequenceNo(sequenceNo)
                .replyTo(replyTo)
                .role(role)
                .messageType(type)
                .content(content)
                .status(ChatMessage.Status.COMPLETED)
                .completedAt(Instant.now())
                .build();
        entityManager.persist(message);
        return message;
    }

    private ChatExecution execution(ChatSession session, ChatMessage input, ChatMessage output) {
        ChatExecution execution = ChatExecution.builder()
                .session(session)
                .inputMessage(input)
                .outputMessage(output)
                .status(ChatExecution.Status.COMPLETED)
                .endedAt(Instant.now())
                .build();
        entityManager.persist(execution);
        entityManager.flush();
        return execution;
    }
}
