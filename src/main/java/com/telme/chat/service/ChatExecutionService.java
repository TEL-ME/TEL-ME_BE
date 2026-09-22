package com.telme.chat.service;

import com.telme.chat.converter.ChatMessageConverter;
import com.telme.chat.entity.ChatExecution;
import com.telme.chat.entity.ChatMessage;
import com.telme.chat.entity.ChatSession;
import com.telme.chat.exception.ChatErrorCode;
import com.telme.chat.repository.ChatExecutionRepository;
import com.telme.chat.repository.ChatSessionRepository;
import com.telme.global.common.exception.GeneralException;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ChatExecutionService {

    private final ChatExecutionRepository chatExecutionRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageAppender chatMessageAppender;
    private final ChatMessageConverter chatMessageConverter;
    private final ApplicationEventPublisher eventPublisher;

    public ChatOutputMessage startAnswer(Long executionId) {
        ChatExecution execution = getRunningExecution(executionId);
        if (execution.getOutputMessage() != null) {
            throw new GeneralException(ChatErrorCode.ANSWER_ALREADY_STARTED);
        }
        ChatSession session = lockSession(execution);

        ChatMessage message = appendAssistantMessage(session, execution, ChatMessage.MessageType.ANSWER);
        execution.attachOutput(message);
        session.touch(Instant.now());
        return flushed(execution, message);
    }

    public ChatOutputMessage completeAnswer(Long executionId, ChatAnswer answer) {
        ChatExecution execution = getRunningExecution(executionId);
        ChatSession session = lockSession(execution);
        Instant completedAt = Instant.now();

        ChatMessage message = outputMessage(session, execution, answer.messageType());
        message.complete(
                answer.messageType(),
                answer.content(),
                answer.answerBasis(),
                chatMessageConverter.toJson(answer.followUps()),
                chatMessageConverter.toJson(answer.storeResults()),
                completedAt
        );
        execution.complete(message, completedAt);
        session.resume();
        session.touch(completedAt);
        ChatOutputMessage output = flushed(execution, message);
        requestPostProcessing(execution, message.getSequenceNo());
        return output;
    }

    public ChatOutputMessage askClarification(Long executionId, String question) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("되묻기 질문은 비어 있을 수 없습니다.");
        }

        ChatExecution execution = getRunningExecution(executionId);
        ChatSession session = lockSession(execution);
        Instant completedAt = Instant.now();

        ChatMessage message = outputMessage(session, execution, ChatMessage.MessageType.CLARIFICATION);
        message.complete(ChatMessage.MessageType.CLARIFICATION, question, null, null, null, completedAt);
        execution.complete(message, completedAt);
        session.waitForClarification();
        session.touch(completedAt);
        ChatOutputMessage output = flushed(execution, message);
        requestPostProcessing(execution, message.getSequenceNo());
        return output;
    }

    public ChatExecutionState completeWithoutOutput(Long executionId) {
        ChatExecution execution = getRunningExecution(executionId);
        if (execution.getOutputMessage() != null) {
            throw new GeneralException(ChatErrorCode.ANSWER_ALREADY_STARTED);
        }
        ChatSession session = lockSession(execution);
        Instant endedAt = Instant.now();

        execution.completeWithoutOutput(endedAt);
        session.touch(endedAt);
        chatExecutionRepository.flush();
        ChatExecutionState state = ChatExecutionState.of(execution);
        requestPostProcessing(execution, execution.getInputMessage().getSequenceNo());
        return state;
    }

    public ChatOutputMessage fail(Long executionId, ChatFailure failure) {
        ChatExecution execution = getRunningExecution(executionId);
        ChatSession session = lockSession(execution);
        Instant endedAt = Instant.now();

        ChatMessage message = outputMessage(session, execution, ChatMessage.MessageType.ERROR);
        message.fail(failure.status(), endedAt);
        execution.fail(failure.executionStatus(), failure.errorCode(), message, endedAt);
        session.touch(endedAt);
        return flushed(execution, message);
    }

    private ChatOutputMessage flushed(ChatExecution execution, ChatMessage message) {
        chatExecutionRepository.flush();
        return ChatOutputMessage.of(execution, message);
    }

    private ChatExecution getRunningExecution(Long executionId) {
        ChatExecution execution = chatExecutionRepository.findExecutionByIdForUpdate(executionId)
                .orElseThrow(() -> new GeneralException(ChatErrorCode.EXECUTION_NOT_FOUND));
        if (!execution.isRunning()) {
            throw new GeneralException(ChatErrorCode.EXECUTION_NOT_RUNNING);
        }
        return execution;
    }

    private ChatSession lockSession(ChatExecution execution) {
        return chatSessionRepository.findSessionByIdForUpdate(execution.getSession().getSessionId())
                .orElseThrow(() -> new GeneralException(ChatErrorCode.SESSION_NOT_FOUND));
    }

    private ChatMessage outputMessage(
            ChatSession session,
            ChatExecution execution,
            ChatMessage.MessageType messageType
    ) {
        ChatMessage started = execution.getOutputMessage();
        return started != null ? started : appendAssistantMessage(session, execution, messageType);
    }

    private ChatMessage appendAssistantMessage(
            ChatSession session,
            ChatExecution execution,
            ChatMessage.MessageType messageType
    ) {
        return chatMessageAppender.append(session, ChatMessage.builder()
                .replyTo(execution.getInputMessage())
                .role(ChatMessage.Role.ASSISTANT)
                .messageType(messageType)
                .status(ChatMessage.Status.GENERATING));
    }

    private void requestPostProcessing(ChatExecution execution, Integer completedThroughSequenceNo) {
        eventPublisher.publishEvent(new ChatSummaryRequested(
                execution.getExecutionId(),
                execution.getSession().getSessionId(),
                completedThroughSequenceNo));
        requestSessionTitle(execution);
    }

    private void requestSessionTitle(ChatExecution execution) {
        ChatSession session = execution.getSession();
        ChatMessage inputMessage = execution.getInputMessage();
        if ((session.getTitle() != null && !session.getTitle().isBlank())
                || inputMessage.getSequenceNo() != 1) {
            return;
        }
        eventPublisher.publishEvent(new ChatSessionTitleRequested(
                execution.getExecutionId(),
                session.getSessionId(),
                inputMessage.getContent()));
    }
}
