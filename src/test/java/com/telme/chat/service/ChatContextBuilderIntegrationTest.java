package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.telme.chat.config.ChatContextProperties;
import com.telme.chat.entity.ChatExecution;
import com.telme.chat.entity.ChatMessage;
import com.telme.chat.entity.ChatSession;
import com.telme.chat.repository.ChatExecutionRepository;
import com.telme.chat.repository.ChatMessageRepository;
import com.telme.member.entity.User;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ChatContextBuilderIntegrationTest {

    @Autowired
    private ChatContextBuilder chatContextBuilder;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private ChatExecutionRepository chatExecutionRepository;

    @Autowired
    private ChatTokenEstimator chatTokenEstimator;

    @Autowired
    private EntityManager entityManager;

    @Test
    void buildsContextFromCompletedMessagesBeforeCurrentQuestion() {
        ChatSession session = createSession("사용자는 번호이동 매장을 찾고 있다.");
        ChatMessage firstQuestion = message(
                session, 1, ChatMessage.Role.USER, ChatMessage.MessageType.QUESTION,
                ChatMessage.Status.COMPLETED, "가까운 매장 알려줘", null);
        message(
                session, 2, ChatMessage.Role.ASSISTANT, ChatMessage.MessageType.ANSWER,
                ChatMessage.Status.GENERATING, "생성 중인 답변", null);
        message(
                session, 3, ChatMessage.Role.ASSISTANT, ChatMessage.MessageType.ERROR,
                ChatMessage.Status.FAILED, "모델 호출 실패", null);
        ChatMessage storeResult = message(
                session, 4, ChatMessage.Role.ASSISTANT, ChatMessage.MessageType.STORE_RESULT,
                ChatMessage.Status.COMPLETED, null, "[{\"storeId\":3,\"name\":\"강남점\"}]", firstQuestion);
        ChatMessage currentQuestion = message(
                session, 5, ChatMessage.Role.USER, ChatMessage.MessageType.QUESTION,
                ChatMessage.Status.COMPLETED, "첫 번째 매장은 어디야?", null);
        message(
                session, 6, ChatMessage.Role.ASSISTANT, ChatMessage.MessageType.ANSWER,
                ChatMessage.Status.COMPLETED, "현재 질문 뒤에 저장된 메시지", null, currentQuestion);

        ChatSession otherSession = createSession(null);
        message(
                otherSession, 1, ChatMessage.Role.USER, ChatMessage.MessageType.QUESTION,
                ChatMessage.Status.COMPLETED, "다른 세션 질문", null);

        ChatExecution execution = execution(session, currentQuestion, ChatExecution.Status.RUNNING);
        entityManager.clear();
        ChatContext context = chatContextBuilder.build(new ChatProcessingCommand(
                execution.getExecutionId(), session.getSessionId(), currentQuestion.getMessageId(), "변조된 질문"),
                4_096);

        assertThat(context.sessionId()).isEqualTo(session.getSessionId());
        assertThat(context.inputMessageId()).isEqualTo(currentQuestion.getMessageId());
        assertThat(context.summary()).isEqualTo("사용자는 번호이동 매장을 찾고 있다.");
        assertThat(context.currentQuestion()).isEqualTo("첫 번째 매장은 어디야?");
        assertThat(context.history())
                .extracting(ChatContextMessage::messageId)
                .containsExactly(firstQuestion.getMessageId(), storeResult.getMessageId());
        assertThat(context.history().getLast().content()).isNull();
        assertThat(context.history().getLast().storeResults()).contains("강남점");
        assertThat(context.estimatedContextTokens()).isPositive();
    }

    @Test
    void keepsQuestionAndAnswerTogetherAtTokenBoundary() {
        ChatSession session = createSession(null);
        ChatMessage question = message(
                session, 1, ChatMessage.Role.USER, ChatMessage.MessageType.QUESTION,
                ChatMessage.Status.COMPLETED, "가나다라", null);
        ChatMessage answer = message(
                session, 2, ChatMessage.Role.ASSISTANT, ChatMessage.MessageType.ANSWER,
                ChatMessage.Status.COMPLETED, "마바사아", null, question);
        ChatMessage current = message(
                session, 3, ChatMessage.Role.USER, ChatMessage.MessageType.QUESTION,
                ChatMessage.Status.COMPLETED, "현재 질문", null);
        ChatExecution execution = execution(session, current, ChatExecution.Status.RUNNING);
        entityManager.clear();
        ChatContextBuilder limitedBuilder = new ChatContextBuilder(
                chatMessageRepository,
                chatExecutionRepository,
                new ChatContextProperties(16, 8),
                chatTokenEstimator
        );

        ChatContext context = limitedBuilder.build(new ChatProcessingCommand(
                execution.getExecutionId(), session.getSessionId(), current.getMessageId(), current.getContent()),
                16);

        assertThat(context.history()).isEmpty();
        assertThat(context.estimatedContextTokens()).isEqualTo(8);

        ChatContextBuilder exactBudgetBuilder = new ChatContextBuilder(
                chatMessageRepository,
                chatExecutionRepository,
                new ChatContextProperties(16, 16),
                chatTokenEstimator
        );
        ChatContext exactBudgetContext = exactBudgetBuilder.build(new ChatProcessingCommand(
                execution.getExecutionId(), session.getSessionId(), current.getMessageId(), current.getContent()),
                24);

        assertThat(exactBudgetContext.history())
                .extracting(ChatContextMessage::messageId)
                .containsExactly(question.getMessageId(), answer.getMessageId());
        assertThat(exactBudgetContext.estimatedContextTokens()).isEqualTo(24);
    }

    @Test
    void rejectsMissingMismatchedStoppedOrNonQuestionExecution() {
        ChatSession session = createSession(null);
        ChatMessage answer = message(
                session, 1, ChatMessage.Role.ASSISTANT, ChatMessage.MessageType.ANSWER,
                ChatMessage.Status.COMPLETED, "답변", null);
        ChatExecution invalidInputExecution = execution(session, answer, ChatExecution.Status.RUNNING);
        entityManager.clear();

        assertThatThrownBy(() -> chatContextBuilder.build(new ChatProcessingCommand(
                Long.MAX_VALUE, session.getSessionId(), Long.MAX_VALUE, "없는 질문"), 100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("채팅 실행을 찾을 수 없습니다.");
        assertThatThrownBy(() -> chatContextBuilder.build(new ChatProcessingCommand(
                invalidInputExecution.getExecutionId(), session.getSessionId(), answer.getMessageId(),
                answer.getContent()), 100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("완료된 사용자 질문만 Context 기준 메시지로 사용할 수 있습니다.");

        ChatMessage question = message(
                session, 2, ChatMessage.Role.USER, ChatMessage.MessageType.QUESTION,
                ChatMessage.Status.COMPLETED, "질문", null);
        ChatExecution stoppedExecution = execution(session, question, ChatExecution.Status.COMPLETED);
        entityManager.clear();
        assertThatThrownBy(() -> chatContextBuilder.build(new ChatProcessingCommand(
                stoppedExecution.getExecutionId(), session.getSessionId(), question.getMessageId(), "질문"), 100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("실행 정보와 Context 기준 메시지가 일치하지 않습니다.");
        assertThatThrownBy(() -> chatContextBuilder.build(new ChatProcessingCommand(
                invalidInputExecution.getExecutionId(), session.getSessionId() + 1, answer.getMessageId(),
                answer.getContent()), 100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("실행 정보와 Context 기준 메시지가 일치하지 않습니다.");

        ChatSession otherSession = createSession(null);
        ChatExecution crossSessionExecution = execution(otherSession, question, ChatExecution.Status.RUNNING);
        entityManager.clear();
        assertThatThrownBy(() -> chatContextBuilder.build(new ChatProcessingCommand(
                crossSessionExecution.getExecutionId(), otherSession.getSessionId(), question.getMessageId(),
                question.getContent()), 100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("실행 정보와 Context 기준 메시지가 일치하지 않습니다.");
    }

    @Test
    void appliesMessageCountLimitByCompleteExchange() {
        ChatSession session = createSession(null);
        ChatMessage oldQuestion = message(
                session, 1, ChatMessage.Role.USER, ChatMessage.MessageType.QUESTION,
                ChatMessage.Status.COMPLETED, "오래된 질문", null);
        message(
                session, 2, ChatMessage.Role.ASSISTANT, ChatMessage.MessageType.ANSWER,
                ChatMessage.Status.COMPLETED, "오래된 답변", null, oldQuestion);
        ChatMessage recentQuestion = message(
                session, 3, ChatMessage.Role.USER, ChatMessage.MessageType.QUESTION,
                ChatMessage.Status.COMPLETED, "최근 질문", null);
        ChatMessage recentAnswer = message(
                session, 4, ChatMessage.Role.ASSISTANT, ChatMessage.MessageType.ANSWER,
                ChatMessage.Status.COMPLETED, "최근 답변", null, recentQuestion);
        ChatMessage current = message(
                session, 5, ChatMessage.Role.USER, ChatMessage.MessageType.QUESTION,
                ChatMessage.Status.COMPLETED, "현재 질문", null);
        ChatExecution execution = execution(session, current, ChatExecution.Status.RUNNING);
        entityManager.clear();
        ChatContextBuilder limitedBuilder = new ChatContextBuilder(
                chatMessageRepository,
                chatExecutionRepository,
                new ChatContextProperties(2, 4_096),
                chatTokenEstimator
        );

        ChatContext context = limitedBuilder.build(new ChatProcessingCommand(
                execution.getExecutionId(), session.getSessionId(), current.getMessageId(), current.getContent()),
                4_096);

        assertThat(context.history())
                .extracting(ChatContextMessage::messageId)
                .containsExactly(recentQuestion.getMessageId(), recentAnswer.getMessageId());
    }

    @Test
    void returnsEmptyHistoryForFirstQuestionAndCapsCallerBudget() {
        ChatSession session = createSession(" ");
        ChatMessage current = message(
                session, 1, ChatMessage.Role.USER, ChatMessage.MessageType.QUESTION,
                ChatMessage.Status.COMPLETED, "첫 질문", null);
        ChatExecution execution = execution(session, current, ChatExecution.Status.RUNNING);
        entityManager.clear();

        ChatContext context = chatContextBuilder.build(new ChatProcessingCommand(
                execution.getExecutionId(), session.getSessionId(), current.getMessageId(), current.getContent()),
                8);

        assertThat(context.summary()).isNull();
        assertThat(context.history()).isEmpty();
        assertThat(context.estimatedContextTokens()).isEqualTo(7);
    }

    @Test
    void dropsSummaryWhenNeededAndRejectsQuestionLargerThanContextBudget() {
        ChatSession session = createSession("가나다라");
        ChatMessage current = message(
                session, 1, ChatMessage.Role.USER, ChatMessage.MessageType.QUESTION,
                ChatMessage.Status.COMPLETED, "마바사아", null);
        ChatExecution execution = execution(session, current, ChatExecution.Status.RUNNING);
        entityManager.clear();
        ChatProcessingCommand command = new ChatProcessingCommand(
                execution.getExecutionId(), session.getSessionId(), current.getMessageId(), current.getContent());

        ChatContext context = chatContextBuilder.build(command, 8);

        assertThat(context.summary()).isNull();
        assertThat(context.currentQuestion()).isEqualTo("마바사아");
        assertThat(context.estimatedContextTokens()).isEqualTo(8);
        assertThatThrownBy(() -> chatContextBuilder.build(command, 7))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("현재 질문이 Context 토큰 예산을 초과합니다.");
    }

    private ChatSession createSession(String summary) {
        User user = User.builder()
                .email("context-" + UUID.randomUUID() + "@example.com")
                .name("context")
                .build();
        entityManager.persist(user);

        ChatSession session = ChatSession.builder()
                .userId(user.getUserId())
                .summary(summary)
                .build();
        entityManager.persist(session);
        entityManager.flush();
        return session;
    }

    private ChatMessage message(
            ChatSession session,
            int sequenceNo,
            ChatMessage.Role role,
            ChatMessage.MessageType messageType,
            ChatMessage.Status status,
            String content,
            String storeResults
    ) {
        return message(session, sequenceNo, role, messageType, status, content, storeResults, null);
    }

    private ChatMessage message(
            ChatSession session,
            int sequenceNo,
            ChatMessage.Role role,
            ChatMessage.MessageType messageType,
            ChatMessage.Status status,
            String content,
            String storeResults,
            ChatMessage replyTo
    ) {
        ChatMessage message = ChatMessage.builder()
                .session(session)
                .sequenceNo(sequenceNo)
                .replyTo(replyTo)
                .role(role)
                .messageType(messageType)
                .status(status)
                .content(content)
                .storeResults(storeResults)
                .completedAt(status == ChatMessage.Status.COMPLETED ? Instant.now() : null)
                .build();
        entityManager.persist(message);
        entityManager.flush();
        return message;
    }

    private ChatExecution execution(
            ChatSession session,
            ChatMessage inputMessage,
            ChatExecution.Status status
    ) {
        ChatExecution execution = ChatExecution.builder()
                .session(session)
                .inputMessage(inputMessage)
                .status(status)
                .build();
        entityManager.persist(execution);
        entityManager.flush();
        return execution;
    }
}
