package com.telme.chat.service;

import com.telme.chat.converter.ChatMessageConverter;
import com.telme.chat.converter.ChatSessionConverter;
import com.telme.chat.dto.req.ChatExecutionRetryRequest;
import com.telme.chat.dto.req.ChatMessageSendRequest;
import com.telme.chat.dto.req.ChatSessionCreateRequest;
import com.telme.chat.dto.req.ChatSessionTitleUpdateRequest;
import com.telme.chat.dto.res.ChatMessageHistoryItemResponse;
import com.telme.chat.dto.res.ChatMessageHistoryResponse;
import com.telme.chat.dto.res.ChatMessageSendResponse;
import com.telme.chat.dto.res.ChatSessionCreateResponse;
import com.telme.chat.dto.res.ChatSessionListItemResponse;
import com.telme.chat.dto.res.ChatSessionListResponse;
import com.telme.chat.dto.res.ChatSessionUpdateResponse;
import com.telme.chat.entity.ChatExecution;
import com.telme.chat.entity.ChatMessage;
import com.telme.chat.entity.ChatSession;
import com.telme.chat.exception.ChatErrorCode;
import com.telme.chat.guard.ChatInputGuardService;
import com.telme.chat.repository.ChatExecutionRepository;
import com.telme.chat.repository.ChatMessageRepository;
import com.telme.chat.repository.ChatSessionRepository;
import com.telme.global.common.exception.GeneralException;
import com.telme.llm.exception.LlmErrorCode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatSessionService {

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatExecutionRepository chatExecutionRepository;
    private final ChatMessageAppender chatMessageAppender;
    private final ChatSessionConverter chatSessionConverter;
    private final ChatMessageConverter chatMessageConverter;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectProvider<ChatFeedbackReader> chatFeedbackReader;
    private final ChatInputGuardService inputGuard;
    private final GuestDailyChatLimit guestDailyChatLimit;

    @Transactional
    public ChatSessionCreateResponse createSession(ChatActor actor, ChatSessionCreateRequest request) {
        ChatSession.ChatSessionBuilder builder = ChatSession.builder()
                .title(normalizeNullableTitle(request.title()));

        if (actor.isMember()) {
            builder.userId(actor.userId());
        } else {
            builder.guestId(actor.guestId());
        }

        return chatSessionConverter.toCreateResponse(chatSessionRepository.save(builder.build()));
    }

    public ChatSessionListResponse getSessions(ChatActor actor, String encodedCursor, int size) {
        ChatSessionCursor cursor = ChatSessionCursor.decode(encodedCursor);
        PageRequest limit = PageRequest.of(0, size + 1);

        List<ChatSession> queried = findSessions(actor, cursor, limit);

        boolean hasNext = queried.size() > size;
        List<ChatSession> page = new ArrayList<>(queried.subList(0, Math.min(size, queried.size())));
        String nextCursor = hasNext ? ChatSessionCursor.encode(page.get(page.size() - 1)) : null;
        List<ChatSessionListItemResponse> sessions = page.stream()
                .map(chatSessionConverter::toListItemResponse)
                .toList();

        return new ChatSessionListResponse(sessions, nextCursor, hasNext);
    }

    public ChatMessageHistoryResponse getMessages(
            ChatActor actor,
            Long sessionId,
            Integer beforeSequenceNo,
            int size
    ) {
        validateSessionOwner(actor, sessionId);

        PageRequest limit = PageRequest.of(0, size + 1);
        List<ChatMessage> queried = beforeSequenceNo == null
                ? chatMessageRepository.findLatestMessages(sessionId, limit)
                : chatMessageRepository.findMessagesBefore(sessionId, beforeSequenceNo, limit);

        boolean hasOlderMessages = queried.size() > size;
        List<ChatMessage> page = new ArrayList<>(queried.subList(0, Math.min(size, queried.size())));
        Integer nextBeforeSequenceNo = hasOlderMessages
                ? page.get(page.size() - 1).getSequenceNo()
                : null;

        Collections.reverse(page);
        ChatFeedbackReader reader = chatFeedbackReader.getIfAvailable();
        Map<Long, ChatFeedbackReader.State> feedback = reader == null ? Map.of() : reader.read(actor, page);
        List<ChatMessageHistoryItemResponse> messages = page.stream()
                .map(message -> {
                    ChatFeedbackReader.State state = feedback.getOrDefault(
                            message.getMessageId(), ChatFeedbackReader.State.UNAVAILABLE);
                    return chatMessageConverter.toHistoryItemResponse(message, state.ratable(), state.myFeedback());
                })
                .toList();
        Long runningExecutionId = findRunningExecution(sessionId)
                .map(ChatExecution::getExecutionId)
                .orElse(null);

        Long retryableExecutionId = chatExecutionRepository
                .findFirstBySession_SessionIdOrderByExecutionIdDesc(sessionId)
                .filter(ChatSessionService::isRetryable)
                .map(ChatExecution::getExecutionId)
                .orElse(null);

        return new ChatMessageHistoryResponse(
                messages, nextBeforeSequenceNo, hasOlderMessages, runningExecutionId, retryableExecutionId);
    }

    public ChatExecutionState getExecution(ChatActor actor, Long executionId) {
        return (actor.isMember()
                ? chatExecutionRepository.findMemberExecution(executionId, actor.userId())
                : chatExecutionRepository.findGuestExecution(executionId, actor.guestId()))
                .map(ChatExecutionState::of)
                .orElseThrow(() -> new GeneralException(ChatErrorCode.EXECUTION_NOT_FOUND));
    }

    private List<ChatSession> findSessions(
            ChatActor actor,
            ChatSessionCursor cursor,
            PageRequest limit
    ) {
        if (cursor.lastActiveAt() == null) {
            return actor.isMember()
                    ? chatSessionRepository.findFirstMemberSessions(actor.userId(), limit)
                    : chatSessionRepository.findFirstGuestSessions(actor.guestId(), limit);
        }

        return actor.isMember()
                ? chatSessionRepository.findMemberSessionsBefore(
                        actor.userId(), cursor.lastActiveAt(), cursor.sessionId(), limit)
                : chatSessionRepository.findGuestSessionsBefore(
                        actor.guestId(), cursor.lastActiveAt(), cursor.sessionId(), limit);
    }

    @Transactional
    public ChatSessionUpdateResponse updateTitle(
            ChatActor actor,
            Long sessionId,
            ChatSessionTitleUpdateRequest request
    ) {
        ChatSession session = getOwnedSessionForUpdate(actor, sessionId);
        session.rename(request.title().trim());
        return chatSessionConverter.toUpdateResponse(session);
    }

    @Transactional
    public ChatSessionUpdateResponse closeSession(ChatActor actor, Long sessionId) {
        ChatSession session = getOwnedSessionForUpdate(actor, sessionId);
        if (session.getStatus() != ChatSession.Status.CLOSED) {
            session.close();
        }
        return chatSessionConverter.toUpdateResponse(session);
    }

    @Transactional
    public ChatMessageSendResponse sendMessage(
            ChatActor actor,
            Long sessionId,
            ChatMessageSendRequest request
    ) {
        return sendMessage(actor, sessionId, request, ChatResponseStopwatch.start());
    }

    // stopwatch는 접수 요청에서 시작한 처리 시간 측정값이다. 처리 명령에 실어 비동기 작업까지 넘긴다.
    @Transactional
    public ChatMessageSendResponse sendMessage(
            ChatActor actor,
            Long sessionId,
            ChatMessageSendRequest request,
            ChatResponseStopwatch stopwatch
    ) {
        ChatCoordinates coordinates = ChatCoordinates.optional(request.latitude(), request.longitude());
        ChatSession session = getOwnedSessionForUpdate(actor, sessionId);
        if (session.getStatus() == ChatSession.Status.CLOSED) {
            throw new GeneralException(ChatErrorCode.SESSION_CLOSED);
        }
        var guarded = inputGuard.begin(actor, sessionId, request);
        if (guarded.cached() != null) {
            return guarded.cached();
        }
        if (guarded.rejected()) {
            if (guarded.alreadyRestricted()) {
                return new ChatMessageSendResponse(sessionId, null, null, null, null,
                        guarded.now(), guarded.notice());
            }
            ChatMessage blocked = chatMessageAppender.append(session, ChatMessage.builder()
                    .role(ChatMessage.Role.USER)
                    .messageType(ChatMessage.MessageType.BLOCKED)
                    .content(guarded.inspection().content())
                    .status(ChatMessage.Status.COMPLETED)
                    .completedAt(guarded.now()));
            session.touch(guarded.now());
            var response = new ChatMessageSendResponse(sessionId, blocked.getMessageId(),
                    blocked.getSequenceNo(), null, null, blocked.getCreatedAt(), guarded.notice());
            inputGuard.record(guarded, actor, sessionId, request, response);
            return response;
        }
        if (findRunningExecution(sessionId).isPresent()) {
            throw new GeneralException(ChatErrorCode.EXECUTION_IN_PROGRESS);
        }
        Integer guestQuestionsRemaining = guestDailyChatLimit.check(actor);

        Instant completedAt = Instant.now();
        ChatMessage message = chatMessageAppender.append(session, ChatMessage.builder()
                .role(ChatMessage.Role.USER)
                .messageType(ChatMessage.MessageType.QUESTION)
                .content(guarded.inspection().content())
                .status(ChatMessage.Status.COMPLETED)
                .completedAt(completedAt));

        ChatExecution execution = chatExecutionRepository.save(ChatExecution.builder()
                .session(session)
                .inputMessage(message)
                .status(ChatExecution.Status.RUNNING)
                .build());

        session.touch(completedAt);
        eventPublisher.publishEvent(new ChatProcessingCommand(execution.getExecutionId(), sessionId,
                message.getMessageId(), message.getContent(), coordinates, stopwatch));
        ChatMessageSendResponse response = chatMessageConverter.toSendResponse(message, execution)
                .withGuestQuestionsRemaining(guestQuestionsRemaining);
        if (guarded.notice() != null) {
            response = response.withInputGuard(guarded.notice());
        }
        inputGuard.record(guarded, actor, sessionId, request, response);
        return response;
    }

    // 답변 생성이 실패해 사용자가 다시 시도할 수 있는 실행인지. 같은 입력 메시지를 새 실행으로 다시 처리한다.
    // 새 질문이 아니므로 입력 가드·게스트 일일 제한·질문 메시지 저장을 거치지 않는다.
    @Transactional
    public ChatMessageSendResponse retryExecution(
            ChatActor actor,
            Long sessionId,
            Long executionId,
            ChatExecutionRetryRequest request,
            ChatResponseStopwatch stopwatch
    ) {
        ChatCoordinates coordinates = request == null
                ? null : ChatCoordinates.optional(request.latitude(), request.longitude());
        ChatSession session = getOwnedSessionForUpdate(actor, sessionId);
        if (session.getStatus() == ChatSession.Status.CLOSED) {
            throw new GeneralException(ChatErrorCode.SESSION_CLOSED);
        }
        ChatExecution failed = chatExecutionRepository.findContextExecution(executionId)
                .filter(execution -> sessionId.equals(execution.getSession().getSessionId()))
                .orElseThrow(() -> new GeneralException(ChatErrorCode.EXECUTION_NOT_FOUND));
        ChatMessage inputMessage = failed.getInputMessage();
        if (chatExecutionRepository.existsByInputMessage_MessageIdAndAttemptGreaterThan(
                inputMessage.getMessageId(), 1)) {
            throw new GeneralException(ChatErrorCode.RETRY_ALREADY_USED);
        }
        if (findRunningExecution(sessionId).isPresent()) {
            throw new GeneralException(ChatErrorCode.EXECUTION_IN_PROGRESS);
        }
        // 그 뒤에 다른 질문이 이어졌다면 이미 지나간 답변이다
        boolean latest = chatExecutionRepository.findFirstBySession_SessionIdOrderByExecutionIdDesc(sessionId)
                .map(execution -> execution.getExecutionId().equals(executionId))
                .orElse(false);
        if (!latest || !isRetryable(failed)) {
            throw new GeneralException(ChatErrorCode.RETRY_NOT_AVAILABLE);
        }

        ChatExecution execution = chatExecutionRepository.save(ChatExecution.builder()
                .session(session)
                .inputMessage(inputMessage)
                .status(ChatExecution.Status.RUNNING)
                .attempt(failed.getAttempt() + 1)
                .build());
        session.touch(Instant.now());
        eventPublisher.publishEvent(new ChatProcessingCommand(execution.getExecutionId(), sessionId,
                inputMessage.getMessageId(), inputMessage.getContent(), coordinates, stopwatch));
        return chatMessageConverter.toSendResponse(inputMessage, execution);
    }

    // 모델 호출 실패로 끝났고 아직 다시 시도하지 않은 실행
    private static boolean isRetryable(ChatExecution execution) {
        return execution.getStatus() == ChatExecution.Status.FAILED
                && execution.getAttempt() == 1
                && LlmErrorCode.RETRYABLE_FAILURE.getCode().equals(execution.getErrorCode());
    }

    private ChatSession getOwnedSessionForUpdate(ChatActor actor, Long sessionId) {
        return (actor.isMember()
                ? chatSessionRepository.findMemberSessionByIdForUpdate(sessionId, actor.userId())
                : chatSessionRepository.findGuestSessionByIdForUpdate(sessionId, actor.guestId()))
                .orElseThrow(() -> new GeneralException(ChatErrorCode.SESSION_NOT_FOUND));
    }

    private Optional<ChatExecution> findRunningExecution(Long sessionId) {
        return chatExecutionRepository.findFirstBySession_SessionIdAndStatusOrderByStartedAtDesc(
                sessionId, ChatExecution.Status.RUNNING);
    }

    private void validateSessionOwner(ChatActor actor, Long sessionId) {
        boolean owned = actor.isMember()
                ? chatSessionRepository.existsBySessionIdAndUserId(sessionId, actor.userId())
                : chatSessionRepository.existsBySessionIdAndUserIdIsNullAndGuestId(sessionId, actor.guestId());

        if (!owned) {
            throw new GeneralException(ChatErrorCode.SESSION_NOT_FOUND);
        }
    }

    private String normalizeNullableTitle(String title) {
        if (title == null || title.isBlank()) {
            return null;
        }
        return title.trim();
    }
}
