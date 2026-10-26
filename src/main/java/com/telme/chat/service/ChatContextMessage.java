package com.telme.chat.service;

import com.telme.chat.entity.ChatMessage;
import java.util.Objects;

public record ChatContextMessage(
        Long messageId,
        Integer sequenceNo,
        ChatMessage.Role role,
        ChatMessage.MessageType messageType,
        String content,
        String storeResults
) {

    public ChatContextMessage {
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(sequenceNo, "sequenceNo");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(messageType, "messageType");
        content = normalize(content);
        storeResults = normalize(storeResults);
        if (role == ChatMessage.Role.USER && messageType != ChatMessage.MessageType.QUESTION) {
            throw new IllegalArgumentException("사용자 Context 메시지는 질문 유형이어야 합니다.");
        }
        if (role == ChatMessage.Role.ASSISTANT && messageType == ChatMessage.MessageType.QUESTION) {
            throw new IllegalArgumentException("어시스턴트 Context 메시지는 질문 유형일 수 없습니다.");
        }
        if (content == null && storeResults == null) {
            throw new IllegalArgumentException("Context 메시지에는 본문 또는 매장 결과가 필요합니다.");
        }
    }

    static ChatContextMessage from(ChatMessage message) {
        return new ChatContextMessage(
                message.getMessageId(),
                message.getSequenceNo(),
                message.getRole(),
                message.getMessageType(),
                message.getContent(),
                message.getStoreResults()
        );
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
