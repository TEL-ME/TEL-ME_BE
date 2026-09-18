package com.telme.chat.service;

import com.telme.chat.config.ChatExecutionProperties;
import com.telme.chat.converter.ChatMessageConverter;
import com.telme.chat.converter.ChatSessionConverter;
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
import com.telme.chat.repository.ChatExecutionRepository;
import com.telme.chat.repository.ChatMessageRepository;
import com.telme.chat.repository.ChatSessionRepository;
import com.telme.global.common.exception.GeneralException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
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
    private final ChatExecutionProperties chatExecutionProperties;

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
        List<ChatMessageHistoryItemResponse> messages = page.stream()
                .map(chatMessageConverter::toHistoryItemResponse)
                .toList();
        Long runningExecutionId = findRunningExecution(sessionId)
                .map(ChatExecution::getExecutionId)
                .orElse(null);

        return new ChatMessageHistoryResponse(messages, nextBeforeSequenceNo, hasOlderMessages, runningExecutionId);
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
        session.close();
        return chatSessionConverter.toUpdateResponse(session);
    }

    @Transactional
    public ChatMessageSendResponse sendMessage(
            ChatActor actor,
            Long sessionId,
            ChatMessageSendRequest request
    ) {
        ChatSession session = getOwnedSessionForUpdate(actor, sessionId);
        if (session.getStatus() == ChatSession.Status.CLOSED) {
            throw new GeneralException(ChatErrorCode.SESSION_CLOSED);
        }
        if (findRunningExecution(sessionId).isPresent()) {
            throw new GeneralException(ChatErrorCode.EXECUTION_IN_PROGRESS);
        }

        Instant completedAt = Instant.now();
        ChatMessage message = chatMessageAppender.append(session, ChatMessage.builder()
                .role(ChatMessage.Role.USER)
                .messageType(ChatMessage.MessageType.QUESTION)
                .content(request.content())
                .status(ChatMessage.Status.COMPLETED)
                .completedAt(completedAt));

        ChatExecution execution = chatExecutionRepository.save(ChatExecution.builder()
                .session(session)
                .inputMessage(message)
                .status(ChatExecution.Status.RUNNING)
                .build());

        // TODO(ai): 트랜잭션 커밋 후 executionId를 AI 파이프라인에 전달하고 완료·실패 상태를 갱신한다.
        session.touch(completedAt);
        return chatMessageConverter.toSendResponse(message, execution);
    }

    private ChatSession getOwnedSessionForUpdate(ChatActor actor, Long sessionId) {
        return (actor.isMember()
                ? chatSessionRepository.findMemberSessionByIdForUpdate(sessionId, actor.userId())
                : chatSessionRepository.findGuestSessionByIdForUpdate(sessionId, actor.guestId()))
                .orElseThrow(() -> new GeneralException(ChatErrorCode.SESSION_NOT_FOUND));
    }

    private Optional<ChatExecution> findRunningExecution(Long sessionId) {
        Instant startedAfter = Instant.now().minus(chatExecutionProperties.runningTimeout());
        return chatExecutionRepository.findExecutionsStartedAfter(
                        sessionId, ChatExecution.Status.RUNNING, startedAfter, PageRequest.of(0, 1))
                .stream()
                .findFirst();
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
