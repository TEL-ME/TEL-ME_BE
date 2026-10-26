package com.telme.chat.service;

import java.util.UUID;

public record ChatActor(Long userId, UUID guestId) {

    public boolean isMember() {
        return userId != null;
    }
}
