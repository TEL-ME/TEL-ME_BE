package com.telme.chat.service;

import com.telme.chat.entity.ChatMessage;
import com.telme.chat.entity.ChatSession;
import com.telme.chat.repository.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class ChatMessageAppender {

    private final ChatMessageRepository chatMessageRepository;

    ChatMessage append(ChatSession lockedSession, ChatMessage.ChatMessageBuilder message) {
        int nextSequenceNo = chatMessageRepository.findMaxSequenceNo(lockedSession.getSessionId()) + 1;
        return chatMessageRepository.save(message
                .session(lockedSession)
                .sequenceNo(nextSequenceNo)
                .build());
    }
}
