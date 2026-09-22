package com.telme.chat.service;

import com.telme.chat.repository.ChatSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class ChatSessionTitleStore {

    private final ChatSessionRepository chatSessionRepository;

    @Transactional
    public boolean saveIfMissing(Long sessionId, String title) {
        return chatSessionRepository.updateTitleIfMissing(sessionId, title) == 1;
    }
}
