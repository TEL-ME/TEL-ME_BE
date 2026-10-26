package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.telme.chat.entity.ChatSession;
import com.telme.chat.exception.ChatErrorCode;
import com.telme.global.common.exception.GeneralException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ChatSessionCursorTest {

    @Test
    void encodesAndDecodesCursor() {
        ChatSession session = ChatSession.builder()
                .sessionId(15L)
                .userId(1L)
                .lastActiveAt(Instant.parse("2026-09-17T01:02:03Z"))
                .build();

        ChatSessionCursor cursor = ChatSessionCursor.decode(ChatSessionCursor.encode(session));

        assertThat(cursor.lastActiveAt()).isEqualTo(session.getLastActiveAt());
        assertThat(cursor.sessionId()).isEqualTo(session.getSessionId());
    }

    @Test
    void rejectsInvalidCursor() {
        assertThatThrownBy(() -> ChatSessionCursor.decode("not-a-cursor"))
                .isInstanceOf(GeneralException.class)
                .extracting(exception -> ((GeneralException) exception).getErrorCode())
                .isEqualTo(ChatErrorCode.INVALID_CURSOR);
    }
}
