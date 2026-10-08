package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telme.chat.config.ChatSummaryProperties;
import com.telme.chat.converter.ChatSummaryConverter;
import com.telme.chat.entity.ChatExecution;
import com.telme.chat.entity.ChatMessage;
import com.telme.chat.entity.ChatSession;
import com.telme.chat.repository.ChatExecutionRepository;
import com.telme.chat.repository.ChatMessageRepository;
import com.telme.chat.repository.ChatSessionRepository;
import com.telme.member.entity.User;
import com.telme.llm.service.LlmClient;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.List;
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

    @Autowired
    private ChatSummaryConverter converter;

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
                tokenEstimator,
                converter
        );

        ChatSummarySnapshot snapshot = tokenLimitedStore.prepare(
                new ChatSummaryRequested(execution.getExecutionId(), session.getSessionId(), 6)).orElseThrow();

        assertThat(snapshot.throughSequenceNo()).isEqualTo(4);
        assertThat(snapshot.messages())
                .extracting(ChatContextMessage::sequenceNo)
                .containsExactly(1, 2, 3, 4);
    }

    @Test
    void advancesPastAnOversizedExchangeWithoutReusingOlderConditionsOrDeletingOriginals() {
        ChatSession session = createSession();
        ChatMessage initial = message(session, 1, ChatMessage.Role.USER, ChatMessage.MessageType.QUESTION,
                "부모님 명의예요.", null);
        message(session, 2, ChatMessage.Role.ASSISTANT, ChatMessage.MessageType.ANSWER, "명의 안내", initial);
        String memory = converter.validateAndStore("{\"messageIds\":[" + initial.getMessageId() + "]}",
                null, List.of(ChatContextMessage.from(initial)), 512, tokenEstimator);
        assertThat(chatSummaryStore.saveIfCurrent(new ChatSummarySnapshot(1L, session.getSessionId(),
                null, 0, 2, List.of()), memory)).isTrue();

        ChatMessage hugeQuestion = message(session, 3, ChatMessage.Role.USER, ChatMessage.MessageType.QUESTION,
                "제 명의로 정정합니다." + "긴 상담 내용".repeat(1000), null);
        message(session, 4, ChatMessage.Role.ASSISTANT, ChatMessage.MessageType.ANSWER,
                "정정 확인", hugeQuestion);
        ChatMessage lastQuestion = null;
        ChatMessage lastAnswer = null;
        for (int turn = 0; turn < 3; turn++) {
            lastQuestion = message(session, 5 + turn * 2, ChatMessage.Role.USER,
                    ChatMessage.MessageType.QUESTION, "로밍 신청 방법을 확인합니다.", null);
            lastAnswer = message(session, 6 + turn * 2, ChatMessage.Role.ASSISTANT,
                    ChatMessage.MessageType.ANSWER, "로밍 신청 안내", lastQuestion);
        }
        ChatExecution execution = execution(session, lastQuestion, lastAnswer);
        entityManager.flush();
        entityManager.clear();
        var properties = new ChatSummaryProperties(4, 2048, 2, 1024, 16, 1200, 512, true);
        var boundedStore = new ChatSummaryStore(chatSessionRepository, chatMessageRepository,
                chatExecutionRepository, properties, tokenEstimator, converter);
        var request = new ChatSummaryRequested(execution.getExecutionId(), session.getSessionId(), 10);
        ChatSummarySnapshot snapshot = boundedStore.prepare(request).orElseThrow();
        assertThat(snapshot.previousSummary()).isNull();
        assertThat(snapshot.expectedSequenceNo()).isEqualTo(2);
        assertThat(snapshot.throughSequenceNo()).isEqualTo(8);
        assertThat(snapshot.messages()).extracting(ChatContextMessage::sequenceNo).containsExactly(5, 6, 7, 8);

        LlmClient model = mock(LlmClient.class);
        when(model.generate(any())).thenReturn("{\"messageIds\":[" + snapshot.messages().get(2).messageId() + "]}");
        assertThat(new ChatSummaryService(boundedStore, model, properties, converter)
                .summarizeIfNeeded(request)).isTrue();
        verify(model).generate(any());
        entityManager.clear();
        var updated = entityManager.find(ChatSession.class, session.getSessionId());
        assertThat(updated.getSummaryThroughSequenceNo()).isEqualTo(8);
        assertThat(converter.render(updated.getSummary())).doesNotContain("부모님");
        assertThat(entityManager.find(ChatMessage.class, hugeQuestion.getMessageId()).getContent())
                .isEqualTo(hugeQuestion.getContent());
        assertThat(boundedStore.prepare(request)).isEmpty();
    }

    @Test
    void oversizedAssistantAnswerStillPreservesTheCompleteCustomerQuestion() {
        ChatSession session = createSession();
        ChatMessage first = message(session, 1, ChatMessage.Role.USER, ChatMessage.MessageType.QUESTION,
                "아직 명의변경하지 않았어요.", null);
        message(session, 2, ChatMessage.Role.ASSISTANT, ChatMessage.MessageType.ANSWER,
                "긴 안내문".repeat(1000), first);
        ChatMessage lastQuestion = null;
        ChatMessage lastAnswer = null;
        for (int turn = 0; turn < 3; turn++) {
            lastQuestion = message(session, 3 + turn * 2, ChatMessage.Role.USER,
                    ChatMessage.MessageType.QUESTION, "로밍 신청 문의", null);
            lastAnswer = message(session, 4 + turn * 2, ChatMessage.Role.ASSISTANT,
                    ChatMessage.MessageType.ANSWER, "로밍 안내", lastQuestion);
        }
        ChatExecution execution = execution(session, lastQuestion, lastAnswer);
        entityManager.flush();
        entityManager.clear();
        var boundedStore = new ChatSummaryStore(chatSessionRepository, chatMessageRepository,
                chatExecutionRepository, new ChatSummaryProperties(4, 2048, 2, 1024, 16, 1200, 512, true),
                tokenEstimator, converter);
        var snapshot = boundedStore.prepare(new ChatSummaryRequested(
                execution.getExecutionId(), session.getSessionId(), 8)).orElseThrow();
        assertThat(snapshot.messages()).extracting(ChatContextMessage::sequenceNo).containsExactly(1, 3, 4, 5, 6);
        assertThat(snapshot.messages().getFirst().content()).isEqualTo(first.getContent());
        assertThat(snapshot.throughSequenceNo()).isEqualTo(6);
        String input = ChatSummaryPrompt.buildSelectionPrompt(snapshot.previousSummary(), snapshot.messages(),
                512, converter);
        assertThat(tokenEstimator.estimatePromptPart(ChatSummaryPrompt.SELECTION_SYSTEM_PROMPT)
                + tokenEstimator.estimatePromptPart(input)).isLessThanOrEqualTo(1200);
    }

    @Test
    void processesFittableExchangesInSeparateBatchesWithoutSkippingThem() {
        ChatSession session = createSession();
        ChatMessage first = message(session, 1, ChatMessage.Role.USER, ChatMessage.MessageType.QUESTION,
                "로밍 확인 " + "조건".repeat(150), null);
        ChatMessage firstAnswer = message(session, 2, ChatMessage.Role.ASSISTANT,
                ChatMessage.MessageType.ANSWER, "로밍 안내", first);
        ChatMessage second = message(session, 3, ChatMessage.Role.USER, ChatMessage.MessageType.QUESTION,
                "유심 확인 " + "조건".repeat(150), null);
        message(session, 4, ChatMessage.Role.ASSISTANT, ChatMessage.MessageType.ANSWER, "유심 안내", second);
        ChatMessage current = message(session, 5, ChatMessage.Role.USER,
                ChatMessage.MessageType.QUESTION, "문의 완료", null);
        ChatMessage currentAnswer = message(session, 6, ChatMessage.Role.ASSISTANT,
                ChatMessage.MessageType.ANSWER, "안내 완료", current);
        ChatExecution execution = execution(session, current, currentAnswer);
        entityManager.flush();
        entityManager.clear();
        int budget = tokenEstimator.estimatePromptPart(ChatSummaryPrompt.SELECTION_SYSTEM_PROMPT)
                + tokenEstimator.estimatePromptPart(ChatSummaryPrompt.buildSelectionPrompt(null,
                        List.of(ChatContextMessage.from(first), ChatContextMessage.from(firstAnswer)), 512, converter));
        var boundedStore = new ChatSummaryStore(chatSessionRepository, chatMessageRepository,
                chatExecutionRepository, new ChatSummaryProperties(4, 2048, 2, 1024, 16, budget, 512, true),
                tokenEstimator, converter);
        var request = new ChatSummaryRequested(execution.getExecutionId(), session.getSessionId(), 6);
        var snapshot = boundedStore.prepare(request).orElseThrow();
        assertThat(snapshot.messages()).extracting(ChatContextMessage::sequenceNo).containsExactly(1, 2);
        assertThat(boundedStore.saveIfCurrent(snapshot, converter.validateAndStore("{\"messageIds\":["
                + first.getMessageId() + "]}", null, snapshot.messages(), 512, tokenEstimator))).isTrue();
        entityManager.clear();
        var next = boundedStore.prepare(request).orElseThrow();
        assertThat(next.expectedSequenceNo()).isEqualTo(2);
        assertThat(next.messages()).extracting(ChatContextMessage::sequenceNo).containsExactly(3, 4);
        assertThat(next.throughSequenceNo()).isEqualTo(4);
    }

    @Test
    void anEntireOversizedBatchAdvancesWithoutCallingTheModelOnEmptyConversation() {
        ChatSession session = createSession();
        ChatMessage huge = message(session, 1, ChatMessage.Role.USER, ChatMessage.MessageType.QUESTION,
                "긴 상담 내용".repeat(1000), null);
        message(session, 2, ChatMessage.Role.ASSISTANT, ChatMessage.MessageType.ANSWER, "확인", huge);
        ChatMessage current = message(session, 3, ChatMessage.Role.USER, ChatMessage.MessageType.QUESTION,
                "로밍 문의", null);
        ChatMessage answer = message(session, 4, ChatMessage.Role.ASSISTANT, ChatMessage.MessageType.ANSWER,
                "로밍 안내", current);
        ChatExecution execution = execution(session, current, answer);
        entityManager.flush();
        entityManager.clear();
        var properties = new ChatSummaryProperties(4, 2048, 2, 1024, 16, 1200, 512, true);
        var boundedStore = new ChatSummaryStore(chatSessionRepository, chatMessageRepository,
                chatExecutionRepository, properties, tokenEstimator, converter);
        var request = new ChatSummaryRequested(execution.getExecutionId(), session.getSessionId(), 4);
        var snapshot = boundedStore.prepare(request).orElseThrow();
        assertThat(snapshot.messages()).isEmpty();
        assertThat(snapshot.throughSequenceNo()).isEqualTo(2);
        LlmClient model = mock(LlmClient.class);
        assertThat(new ChatSummaryService(boundedStore, model, properties, converter)
                .summarizeIfNeeded(request)).isTrue();
        verify(model, never()).generate(any());
        entityManager.clear();
        assertThat(entityManager.find(ChatSession.class, session.getSessionId()).getSummaryThroughSequenceNo())
                .isEqualTo(2);
        assertThat(boundedStore.prepare(request)).isEmpty();
    }

    @Test
    void keepsEarlierSubjectWhenOnlyAnAssistantAnswerIsTooLarge() {
        ChatSession session = createSession();
        ChatMessage subject = message(session, 1, ChatMessage.Role.USER, ChatMessage.MessageType.QUESTION,
                "유심 재발급 비용을 알고 싶어요.", null);
        message(session, 2, ChatMessage.Role.ASSISTANT, ChatMessage.MessageType.ANSWER, "비용 안내", subject);
        String memory = converter.validateAndStore("{\"messageIds\":[" + subject.getMessageId() + "]}",
                null, List.of(ChatContextMessage.from(subject)), 512, tokenEstimator);
        assertThat(chatSummaryStore.saveIfCurrent(new ChatSummarySnapshot(1L, session.getSessionId(),
                null, 0, 2, List.of()), memory)).isTrue();
        ChatMessage followup = message(session, 3, ChatMessage.Role.USER, ChatMessage.MessageType.QUESTION,
                "그 비용은 아직 확인 중이에요.", null);
        message(session, 4, ChatMessage.Role.ASSISTANT, ChatMessage.MessageType.ANSWER,
                "긴 안내".repeat(1000), followup);
        ChatMessage current = message(session, 5, ChatMessage.Role.USER, ChatMessage.MessageType.QUESTION,
                "로밍 문의", null);
        ChatMessage answer = message(session, 6, ChatMessage.Role.ASSISTANT, ChatMessage.MessageType.ANSWER,
                "로밍 안내", current);
        ChatExecution execution = execution(session, current, answer);
        entityManager.flush();
        entityManager.clear();
        var boundedStore = new ChatSummaryStore(chatSessionRepository, chatMessageRepository,
                chatExecutionRepository, new ChatSummaryProperties(4, 2048, 2, 1024, 16, 1200, 512, true),
                tokenEstimator, converter);
        var snapshot = boundedStore.prepare(new ChatSummaryRequested(
                execution.getExecutionId(), session.getSessionId(), 6)).orElseThrow();
        assertThat(snapshot.previousSummary()).isEqualTo(memory);
        assertThat(snapshot.messages()).extracting(ChatContextMessage::sequenceNo).containsExactly(3);
        assertThat(snapshot.throughSequenceNo()).isEqualTo(4);
        String stored = converter.validateAndStore("{\"messageIds\":[" + followup.getMessageId() + "]}",
                snapshot.previousSummary(), snapshot.messages(), 512, tokenEstimator);
        assertThat(converter.sources(stored)).extracting(ChatContextMessage::sequenceNo).containsExactly(1, 3);
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
