package com.telme.chat.service;

import com.telme.chat.entity.ChatSession;
import com.telme.chat.exception.ChatErrorCode;
import com.telme.global.common.exception.GeneralException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;

record ChatSessionCursor(Instant lastActiveAt, Long sessionId) {

    static ChatSessionCursor firstPage() {
        return new ChatSessionCursor(null, null);
    }

    static ChatSessionCursor decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return firstPage();
        }

        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] values = decoded.split("\\|", 2);
            if (values.length != 2) {
                throw new IllegalArgumentException();
            }
            return new ChatSessionCursor(Instant.parse(values[0]), Long.valueOf(values[1]));
        } catch (IllegalArgumentException | DateTimeParseException exception) {
            throw new GeneralException(ChatErrorCode.INVALID_CURSOR);
        }
    }

    static String encode(ChatSession session) {
        String value = session.getLastActiveAt() + "|" + session.getSessionId();
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
