package com.telme.chat.service;

import com.telme.chat.converter.ChatMessageConverter;
import com.telme.chat.converter.ChatSessionConverter;
import com.telme.chat.dto.req.ChatMessageSendRequest;
import com.telme.chat.dto.req.ChatSessionCreateRequest;
import com.telme.chat.dto.req.ChatSessionTitleUpdateRequest;
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
import java.util.List;
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
    private final ChatSessionConverter chatSessionConverter;
    private final ChatMessageConverter chatMessageConverter;

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

        Instant completedAt = Instant.now();
        int nextSequenceNo = chatMessageRepository.findMaxSequenceNo(sessionId) + 1;
        ChatMessage message = chatMessageRepository.save(ChatMessage.builder()
                .session(session)
                .sequenceNo(nextSequenceNo)
                .role(ChatMessage.Role.USER)
                .messageType(ChatMessage.MessageType.QUESTION)
                .content(request.content())
                .status(ChatMessage.Status.COMPLETED)
                .completedAt(completedAt)
                .build());

        ChatExecution execution = chatExecutionRepository.save(ChatExecution.builder()
                .session(session)
                .inputMessage(message)
                .status(ChatExecution.Status.RUNNING)
                .build());

        session.touch(completedAt);
        return chatMessageConverter.toSendResponse(message, execution);
    }

    private ChatSession getOwnedSessionForUpdate(ChatActor actor, Long sessionId) {
        ChatSession session = chatSessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new GeneralException(ChatErrorCode.SESSION_NOT_FOUND));
        if (!actor.owns(session)) {
            throw new GeneralException(ChatErrorCode.SESSION_NOT_FOUND);
        }
        return session;
    }

    private String normalizeNullableTitle(String title) {
        if (title == null || title.isBlank()) {
            return null;
        }
        return title.trim();
    }
}
