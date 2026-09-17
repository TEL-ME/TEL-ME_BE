package com.telme.chat.converter;

import com.telme.chat.dto.res.ChatSessionCreateResponse;
import com.telme.chat.dto.res.ChatSessionListItemResponse;
import com.telme.chat.dto.res.ChatSessionUpdateResponse;
import com.telme.chat.entity.ChatSession;
import org.springframework.stereotype.Component;

@Component
public class ChatSessionConverter {

    public ChatSessionCreateResponse toCreateResponse(ChatSession session) {
        return new ChatSessionCreateResponse(
                session.getSessionId(),
                session.getTitle(),
                session.getStatus().name(),
                session.getCreatedAt(),
                session.getLastActiveAt()
        );
    }

    public ChatSessionUpdateResponse toUpdateResponse(ChatSession session) {
        return new ChatSessionUpdateResponse(
                session.getSessionId(),
                session.getTitle(),
                session.getStatus().name(),
                session.getCreatedAt(),
                session.getLastActiveAt()
        );
    }

    public ChatSessionListItemResponse toListItemResponse(ChatSession session) {
        return new ChatSessionListItemResponse(
                session.getSessionId(),
                session.getTitle(),
                session.getStatus().name(),
                session.getCreatedAt(),
                session.getLastActiveAt()
        );
    }
}
