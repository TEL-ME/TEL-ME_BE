package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.telme.chat.dto.req.ChatMessageSendRequest;
import com.telme.chat.dto.req.ChatSessionCreateRequest;
import com.telme.chat.dto.res.ChatMessageHistoryItemResponse;
import com.telme.chat.dto.res.ChatMessageHistoryResponse;
import com.telme.chat.dto.res.ChatMessageSendResponse;
import com.telme.chat.entity.ChatExecution;
import com.telme.chat.entity.ChatMessage;
import com.telme.chat.entity.ChatSession;
import com.telme.chat.exception.ChatErrorCode;
import com.telme.global.common.code.BaseErrorCode;
import com.telme.global.common.exception.GeneralException;
import com.telme.member.entity.User;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ChatExecutionServiceIntegrationTest {

    @Autowired
    private ChatSessionService chatSessionService;

    @Autowired
    private ChatExecutionService chatExecutionService;

    @Autowired
    private EntityManager entityManager;

    private ChatActor actor;
    private Long sessionId;

    @BeforeEach
    void setUpSession() {
        User user = User.builder()
                .email("execution-" + UUID.randomUUID() + "@example.com")
                .name("execution")
                .build();
        entityManager.persist(user);
        entityManager.flush();

        actor = new ChatActor(user.getUserId(), null);
        sessionId = chatSessionService.createSession(actor, new ChatSessionCreateRequest("실행 테스트"))
                .sessionId();
    }

    @Test
    void startedAnswerKeepsItsSequenceWhileUserSendsAnotherMessage() {
        ChatMessageSendResponse question = send("5G 요금제 알려줘");

        ChatOutputMessage started = chatExecutionService.startAnswer(question.executionId());
        assertThat(started.sequenceNo()).isEqualTo(2);
        assertThat(started.status()).isEqualTo(ChatMessage.Status.GENERATING);

        ChatMessageSendResponse nextQuestion = send("그리고 로밍은?");
        assertThat(nextQuestion.sequenceNo()).isEqualTo(3);

        ChatOutputMessage completed = chatExecutionService.completeAnswer(question.executionId(), new ChatAnswer(
                ChatMessage.MessageType.ANSWER,
                "5G 요금제는 세 가지입니다.",
                ChatMessage.AnswerBasis.GROUNDED,
                List.of("가장 저렴한 요금제는?", "데이터 무제한은?"),
                null
        ));

        assertThat(completed.messageId()).isEqualTo(started.messageId());
        assertThat(completed.sequenceNo()).isEqualTo(2);
        assertThat(completed.status()).isEqualTo(ChatMessage.Status.COMPLETED);

        ChatExecution execution = findExecution(question.executionId());
        assertThat(execution.getStatus()).isEqualTo(ChatExecution.Status.COMPLETED);
        assertThat(execution.getOutputMessage().getMessageId()).isEqualTo(started.messageId());
        assertThat(execution.getEndedAt()).isNotNull();

        ChatMessageHistoryItemResponse answer = historyItem(2);
        assertThat(answer.role()).isEqualTo("ASSISTANT");
        assertThat(answer.messageType()).isEqualTo("ANSWER");
        assertThat(answer.replyToMessageId()).isEqualTo(question.messageId());
        assertThat(answer.content()).isEqualTo("5G 요금제는 세 가지입니다.");
        assertThat(answer.answerBasis()).isEqualTo("GROUNDED");
        assertThat(answer.followUps()).containsExactly("가장 저렴한 요금제는?", "데이터 무제한은?");
        assertThat(answer.storeResults()).isNull();
        assertThat(answer.completedAt()).isNotNull();
    }

    @Test
    void generatingAnswerIsVisibleInHistory() {
        ChatMessageSendResponse question = send("가까운 매장 알려줘");
        chatExecutionService.startAnswer(question.executionId());

        ChatMessageHistoryItemResponse generating = historyItem(2);
        assertThat(generating.status()).isEqualTo("GENERATING");
        assertThat(generating.content()).isNull();
        assertThat(generating.completedAt()).isNull();
    }

    @Test
    void completesAnswerWithoutStartByAppendingMessage() {
        ChatMessageSendResponse question = send("강남역 매장 알려줘");

        ChatOutputMessage completed = chatExecutionService.completeAnswer(question.executionId(), new ChatAnswer(
                ChatMessage.MessageType.STORE_RESULT,
                "강남역 근처 매장입니다.",
                null,
                null,
                List.of(Map.of("storeId", 1, "name", "강남점"))
        ));

        assertThat(completed.sequenceNo()).isEqualTo(2);
        ChatMessageHistoryItemResponse answer = historyItem(2);
        assertThat(answer.messageType()).isEqualTo("STORE_RESULT");
        assertThat(answer.status()).isEqualTo("COMPLETED");
        assertThat(answer.storeResults()).containsExactly(Map.of("storeId", 1, "name", "강남점"));
    }

    @Test
    void clarificationWaitsForUserAndNextAnswerResumesSession() {
        ChatMessageSendResponse question = send("매장 찾아줘");

        ChatOutputMessage clarification = chatExecutionService.askClarification(
                question.executionId(), "어느 지역 매장을 찾으시나요?");

        assertThat(clarification.messageType()).isEqualTo(ChatMessage.MessageType.CLARIFICATION);
        assertThat(clarification.status()).isEqualTo(ChatMessage.Status.COMPLETED);
        assertThat(findSession().getStatus()).isEqualTo(ChatSession.Status.NEED_CLARIFICATION);
        assertThat(findExecution(question.executionId()).getStatus()).isEqualTo(ChatExecution.Status.COMPLETED);

        ChatMessageSendResponse reply = send("강남역이요");
        assertThat(reply.sequenceNo()).isEqualTo(3);

        chatExecutionService.completeAnswer(reply.executionId(), new ChatAnswer(
                ChatMessage.MessageType.STORE_RESULT, "강남역 근처 매장입니다.", null, null, List.of()));

        assertThat(findSession().getStatus()).isEqualTo(ChatSession.Status.ACTIVE);
    }

    @Test
    void failureWithoutStartedAnswerLeavesErrorMessage() {
        ChatMessageSendResponse question = send("요금제 알려줘");

        ChatOutputMessage failed = chatExecutionService.fail(
                question.executionId(), new ChatFailure(ChatMessage.Status.TIMEOUT, "LLM_TIMEOUT"));

        assertThat(failed.sequenceNo()).isEqualTo(2);
        assertThat(failed.messageType()).isEqualTo(ChatMessage.MessageType.ERROR);
        assertThat(failed.status()).isEqualTo(ChatMessage.Status.TIMEOUT);

        ChatExecution execution = findExecution(question.executionId());
        assertThat(execution.getStatus()).isEqualTo(ChatExecution.Status.FAILED);
        assertThat(execution.getErrorCode()).isEqualTo("LLM_TIMEOUT");
        assertThat(execution.getOutputMessage().getMessageId()).isEqualTo(failed.messageId());
        assertThat(historyItem(2).replyToMessageId()).isEqualTo(question.messageId());
    }

    @Test
    void failureAfterStartMarksGeneratingMessage() {
        ChatMessageSendResponse question = send("요금제 알려줘");
        ChatOutputMessage started = chatExecutionService.startAnswer(question.executionId());

        ChatOutputMessage cancelled = chatExecutionService.fail(
                question.executionId(), new ChatFailure(ChatMessage.Status.CANCELLED, "USER_CANCELLED"));

        assertThat(cancelled.messageId()).isEqualTo(started.messageId());
        assertThat(cancelled.messageType()).isEqualTo(ChatMessage.MessageType.ANSWER);
        assertThat(cancelled.status()).isEqualTo(ChatMessage.Status.CANCELLED);
        assertThat(findExecution(question.executionId()).getStatus()).isEqualTo(ChatExecution.Status.CANCELLED);
    }

    @Test
    void closedSessionStillStoresStartedExecutionWithoutReopening() {
        ChatMessageSendResponse question = send("매장 찾아줘");
        chatSessionService.closeSession(actor, sessionId);

        chatExecutionService.askClarification(question.executionId(), "어느 지역인가요?");

        assertThat(findSession().getStatus()).isEqualTo(ChatSession.Status.CLOSED);
        assertThat(historyItem(2).messageType()).isEqualTo("CLARIFICATION");
    }

    @Test
    void rejectsFinishedOrUnknownExecution() {
        ChatMessageSendResponse question = send("요금제 알려줘");
        chatExecutionService.startAnswer(question.executionId());

        assertErrorCode(
                () -> chatExecutionService.startAnswer(question.executionId()),
                ChatErrorCode.ANSWER_ALREADY_STARTED);

        chatExecutionService.fail(question.executionId(), new ChatFailure(ChatMessage.Status.FAILED, "MODEL_ERROR"));

        assertErrorCode(
                () -> chatExecutionService.completeAnswer(question.executionId(), new ChatAnswer(
                        ChatMessage.MessageType.ANSWER, "늦은 답변", null, null, null)),
                ChatErrorCode.EXECUTION_NOT_RUNNING);
        assertErrorCode(
                () -> chatExecutionService.askClarification(Long.MAX_VALUE, "질문"),
                ChatErrorCode.EXECUTION_NOT_FOUND);
    }

    private ChatMessageSendResponse send(String content) {
        return chatSessionService.sendMessage(actor, sessionId, new ChatMessageSendRequest(content));
    }

    private ChatMessageHistoryItemResponse historyItem(int sequenceNo) {
        entityManager.flush();
        entityManager.clear();
        ChatMessageHistoryResponse history = chatSessionService.getMessages(actor, sessionId, null, 50);
        return history.messages().stream()
                .filter(message -> message.sequenceNo() == sequenceNo)
                .findFirst()
                .orElseThrow();
    }

    private ChatExecution findExecution(Long executionId) {
        entityManager.flush();
        entityManager.clear();
        return entityManager.find(ChatExecution.class, executionId);
    }

    private ChatSession findSession() {
        entityManager.flush();
        entityManager.clear();
        return entityManager.find(ChatSession.class, sessionId);
    }

    private void assertErrorCode(Runnable call, BaseErrorCode expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(GeneralException.class)
                .extracting(exception -> ((GeneralException) exception).getErrorCode())
                .isEqualTo(expected);
    }
}
