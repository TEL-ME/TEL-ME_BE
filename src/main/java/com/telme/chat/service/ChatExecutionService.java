package com.telme.chat.service;

import com.telme.chat.converter.ChatMessageConverter;
import com.telme.chat.entity.ChatExecution;
import com.telme.chat.entity.ChatMessage;
import com.telme.chat.entity.ChatSession;
import com.telme.chat.exception.ChatErrorCode;
import com.telme.chat.repository.ChatExecutionRepository;
import com.telme.chat.repository.ChatInputMaskRepository;
import com.telme.chat.safety.OutputMaskContext;
import com.telme.chat.repository.ChatSessionRepository;
import com.telme.chat.safety.ChatOutputBlockedException;
import com.telme.chat.safety.ChatOutputGuard;
import com.telme.global.common.exception.GeneralException;
import java.time.Instant;
import java.util.List;
import java.util.function.BooleanSupplier;
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
    private final ChatInputMaskRepository inputMasks;
    private final ChatResponseTimer responseTimer;

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
        return completeAnswer(executionId, answer, false);
    }

    public ChatExecutionState completeBasicConversation(Long executionId, ChatAnswer answer) {
        return completeAnswer(executionId, answer, true);
    }

    private ChatExecutionState completeAnswer(Long executionId, ChatAnswer original, boolean preserveConsultation) {
        ChatExecution execution = getRunningExecution(executionId);
        ChatSession session = lockSession(execution);
        ChatAnswer answer = ChatPersona.decorate(execution.getInputMessage().getContent(), original);
        OutputMaskContext masks = readMasks(execution, () -> outputGuard.needsMaskContext(answer));
        verifyOutput(() -> outputGuard.verify(answer, masks));
        String followUps = chatMessageConverter.toJson(answer.followUps());
        String stores = chatMessageConverter.toJson(answer.storeResults());
        String searchContext = chatMessageConverter.toContextJson(answer.storeSearchContext());
        verifyOutput(() -> outputGuard.verifySerialized(answer.content(), followUps, stores, searchContext, masks));
        Ending ending = end(execution);

        ChatMessage message = outputMessage(session, execution, answer.messageType());
        message.complete(
                answer.messageType(),
                answer.content(),
                answer.answerBasis(),
                followUps,
                stores,
                searchContext,
                answer.handoff(),
                ending.at()
        );
        execution.complete(message, ending.at(), ending.totalMs());
        if (!preserveConsultation) session.resume();
        session.touch(ending.at());
        ChatExecutionState output = flushed(execution);
        requestPostProcessing(execution, message.getSequenceNo());
        return output;

    }

    public ChatExecutionState askClarification(Long executionId, String question) {
        return askClarification(executionId, question, List.of());
    }

    public ChatExecutionState askClarification(Long executionId, String originalQuestion, List<String> options) {
        if (originalQuestion == null || originalQuestion.isBlank()) {
            throw new IllegalArgumentException("되묻기 질문은 비어 있을 수 없습니다.");
        }

        ChatExecution execution = getRunningExecution(executionId);
        ChatSession session = lockSession(execution);
        String question = ChatPersona.prependGreeting(execution.getInputMessage().getContent(), originalQuestion);
        OutputMaskContext masks = readMasks(execution,
                () -> outputGuard.needsClarificationMaskContext(question, options));
        verifyOutput(() -> outputGuard.verifyClarification(question, options, masks));
        Ending ending = end(execution);

        ChatMessage message = outputMessage(session, execution, ChatMessage.MessageType.CLARIFICATION);
        // 선택지가 있으면 후속 질문 자리에 실어 보낸다. 화면이 버튼으로 그려 눌러서 답할 수 있다
        String followUps = options == null || options.isEmpty() ? null : chatMessageConverter.toJson(options);
        verifyOutput(() -> outputGuard.verifySerializedClarification(question, followUps, masks));
        message.complete(ChatMessage.MessageType.CLARIFICATION, question, null, followUps, null, ending.at());
        execution.complete(message, ending.at(), ending.totalMs());
        session.waitForClarification();
        session.touch(ending.at());
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
        Ending ending = end(execution);

        execution.completeWithoutOutput(ending.at(), ending.totalMs());
        session.touch(ending.at());
        chatExecutionRepository.flush();
        ChatExecutionState state = ChatExecutionState.of(execution);
        requestPostProcessing(execution, execution.getInputMessage().getSequenceNo());
        return state;
    }

    public ChatExecutionState fail(Long executionId, ChatFailure failure) {
        ChatExecution execution = getRunningExecution(executionId);
        ChatSession session = lockSession(execution);
        Ending ending = end(execution);

        ChatMessage message = outputMessage(session, execution, ChatMessage.MessageType.ERROR);
        if (failure.isOutputSafetyFailure()) {
            // 안전 안내만 남긴다. 차단 후보·근거·추천 질문을 정상 답변 이력으로 저장하지 않는다.
            message.complete(ChatMessage.MessageType.ERROR, failure.message(), null, null, null, ending.at());
        }
        message.fail(failure.status(), ending.at());
        execution.fail(failure.executionStatus(), failure.errorCode(), message, ending.at(), ending.totalMs());
        session.touch(ending.at());
        return flushed(execution);
    }

    private record Ending(Instant at, Integer totalMs) {
    }

    // 종료 시각과 처리 시간은 결과를 실행에 기록하는 이 지점에서 함께 잰다.
    // 처리 시간은 같은 트랜잭션의 남은 상담 상태 저장·커밋과 그 뒤 SSE 전송을 포함하지 않는다.
    // 종료 시각은 started_at·단계 기록 시각과 같은 DB 시계로 남겨, 처리 이력에서 시작·단계·종료 순서가 뒤집히지 않게 한다.
    // 시간 계산은 이 시각이 아니라 total_ms로 한다.
    private Ending end(ChatExecution execution) {
        return new Ending(
                chatExecutionRepository.currentDatabaseTime(),
                responseTimer.findElapsedMillis(execution.getExecutionId()));
    }

    private OutputMaskContext readMasks(ChatExecution execution, BooleanSupplier needed) {
        try {
            if (!needed.getAsBoolean()) {
                return OutputMaskContext.empty();
            }
            return inputMasks.read(execution.getSession().getSessionId(), execution.getInputMessage().getSequenceNo());
        } catch (RuntimeException inspectionFailure) {
            throw new GeneralException(ChatErrorCode.OUTPUT_CHECK_FAILED);
        }
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
