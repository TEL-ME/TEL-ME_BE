package com.telme.chat.service;

import com.telme.chat.entity.ChatSession;
import java.util.Objects;
import java.util.UUID;

public record ChatActor(Long userId, UUID guestId) {

    public boolean isMember() {
        return userId != null;
    }

    public boolean owns(ChatSession session) {
        if (isMember()) {
            return Objects.equals(userId, session.getUserId());
        }
        return session.getUserId() == null && Objects.equals(guestId, session.getGuestId());
    }
}
