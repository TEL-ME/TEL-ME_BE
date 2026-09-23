package com.telme.chat.service;

import com.telme.chat.converter.ChatMessageSourceConverter;
import com.telme.chat.dto.res.ChatMessageSourcesResponse;
import com.telme.chat.exception.ChatErrorCode;
import com.telme.chat.repository.ChatMessageRepository;
import com.telme.global.common.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatMessageSourceService {

    private final ChatMessageRepository chatMessageRepository;
    private final ChatMessageSourceQueryPort chatMessageSourceQueryPort;
    private final ChatMessageSourceConverter chatMessageSourceConverter;

    public ChatMessageSourcesResponse getMessageSources(ChatActor actor, Long messageId) {
        if (!ownsMessage(actor, messageId)) {
            throw new GeneralException(ChatErrorCode.MESSAGE_NOT_FOUND);
        }

        return chatMessageSourceConverter.toResponse(
                messageId,
                chatMessageSourceQueryPort.findByMessageId(messageId)
        );
    }

    private boolean ownsMessage(ChatActor actor, Long messageId) {
        return actor.isMember()
                ? chatMessageRepository.existsByMessageIdAndSession_UserId(messageId, actor.userId())
                : chatMessageRepository.existsByMessageIdAndSession_UserIdIsNullAndSession_GuestId(
                        messageId, actor.guestId());
    }
}
