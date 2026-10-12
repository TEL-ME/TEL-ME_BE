package com.telme.chat.service;

import com.telme.chat.converter.ChatMessageConverter;
import com.telme.chat.entity.ChatExecution;
import com.telme.chat.entity.ChatMessage;
import com.telme.chat.entity.ChatSession;
import com.telme.chat.exception.ChatErrorCode;
import com.telme.chat.repository.ChatExecutionRepository;
import com.telme.chat.repository.ChatSessionRepository;
import com.telme.chat.safety.ChatOutputBlockedException;
import com.telme.chat.safety.ChatOutputGuard;
import com.telme.global.common.exception.GeneralException;
import java.time.Instant;
import java.util.List;
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
    private final ChatOutputGuard outputGuard;

    public ChatExecutionState startAnswer(Long executionId) {
        ChatExecution execution = getRunningExecution(executionId);
        if (execution.getOutputMessage() != null) {
            throw new GeneralException(ChatErrorCode.ANSWER_ALREADY_STARTED);
        }
        ChatSession session = lockSession(execution);

        ChatMessage message = appendAssistantMessage(session, execution, ChatMessage.MessageType.ANSWER);
        execution.attachOutput(message);
        session.touch(Instant.now());
        return flushed(execution);
    }

    public ChatExecutionState completeAnswer(Long executionId, ChatAnswer answer) {
        ChatExecution execution = getRunningExecution(executionId);
        ChatSession session = lockSession(execution);
        verifyOutput(() -> outputGuard.verify(answer));
        String followUps = chatMessageConverter.toJson(answer.followUps());
        String stores = chatMessageConverter.toJson(answer.storeResults());
        String searchContext = chatMessageConverter.toContextJson(answer.storeSearchContext());
        verifyOutput(() -> outputGuard.verifySerialized(answer.content(), followUps, stores, searchContext));
        Instant completedAt = Instant.now();

        ChatMessage message = outputMessage(session, execution, answer.messageType());
        message.complete(
                answer.messageType(),
                answer.content(),
                answer.answerBasis(),
                followUps,
                stores,
                searchContext,
                completedAt
        );
        execution.complete(message, completedAt);
        session.resume();
        session.touch(completedAt);
        ChatExecutionState output = flushed(execution);
        requestPostProcessing(execution, message.getSequenceNo());
        return output;

    }

    public ChatExecutionState askClarification(Long executionId, String question) {
        return askClarification(executionId, question, List.of());
    }

    public ChatExecutionState askClarification(Long executionId, String question, List<String> options) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("되묻기 질문은 비어 있을 수 없습니다.");
        }

        ChatExecution execution = getRunningExecution(executionId);
        ChatSession session = lockSession(execution);
        verifyOutput(() -> outputGuard.verifyClarification(question, options));
        Instant completedAt = Instant.now();

        ChatMessage message = outputMessage(session, execution, ChatMessage.MessageType.CLARIFICATION);
        // 선택지가 있으면 후속 질문 자리에 실어 보낸다. 화면이 버튼으로 그려 눌러서 답할 수 있다
        String followUps = options == null || options.isEmpty() ? null : chatMessageConverter.toJson(options);
        verifyOutput(() -> outputGuard.verifySerializedClarification(question, followUps));
        message.complete(ChatMessage.MessageType.CLARIFICATION, question, null, followUps, null, completedAt);
        execution.complete(message, completedAt);
        session.waitForClarification();
        session.touch(completedAt);
        ChatExecutionState output = flushed(execution);
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

    public ChatExecutionState fail(Long executionId, ChatFailure failure) {
        ChatExecution execution = getRunningExecution(executionId);
        ChatSession session = lockSession(execution);
        Instant endedAt = Instant.now();

        ChatMessage message = outputMessage(session, execution, ChatMessage.MessageType.ERROR);
        if (failure.isOutputSafetyFailure()) {
            // 안전 안내만 남긴다. 차단 후보·근거·추천 질문을 정상 답변 이력으로 저장하지 않는다.
            message.complete(ChatMessage.MessageType.ERROR, failure.message(), null, null, null, endedAt);
        }
        message.fail(failure.status(), endedAt);
        execution.fail(failure.executionStatus(), failure.errorCode(), message, endedAt);
        session.touch(endedAt);
        return flushed(execution);
    }

    private void verifyOutput(Runnable inspection) {
        try {
            inspection.run();
        } catch (ChatOutputBlockedException blocked) {
            throw blocked;
        } catch (RuntimeException inspectionFailure) {
            // 검사 오류도 검증되지 않은 후보를 전송하지 않는다. 원문·예외 본문은 전달하지 않는다.
            throw new GeneralException(ChatErrorCode.OUTPUT_CHECK_FAILED);
        }
    }

    private ChatExecutionState flushed(ChatExecution execution) {
        chatExecutionRepository.flush();
        return ChatExecutionState.of(execution);
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
        if (session.getTitle() != null && !session.getTitle().isBlank()) {
            return;
        }
        eventPublisher.publishEvent(new ChatSessionTitleRequested(
                execution.getExecutionId(),
                session.getSessionId(),
                inputMessage.getContent()));
    }
}
