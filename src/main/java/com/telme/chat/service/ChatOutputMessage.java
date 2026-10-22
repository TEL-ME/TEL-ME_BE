package com.telme.chat.service;

import com.telme.chat.entity.ChatExecution;
import com.telme.chat.entity.ChatMessage;

public record ChatOutputMessage(
        Long sessionId,
        Long executionId,
        Long messageId,
        Integer sequenceNo,
        ChatMessage.MessageType messageType,
        ChatMessage.Status status,
        // FAQ 근거 없음이면 true. 저장하지 않아 히스토리 조회에는 없다
        boolean handoff
) {

    public ChatOutputMessage(Long sessionId, Long executionId, Long messageId, Integer sequenceNo,
            ChatMessage.MessageType messageType, ChatMessage.Status status) {
        this(sessionId, executionId, messageId, sequenceNo, messageType, status, false);
    }

    ChatOutputMessage withHandoff(boolean handoff) {
        return new ChatOutputMessage(sessionId, executionId, messageId, sequenceNo, messageType, status, handoff);
    }

    static ChatOutputMessage of(ChatExecution execution, ChatMessage message) {
        return new ChatOutputMessage(
                message.getSession().getSessionId(),
                execution.getExecutionId(),
                message.getMessageId(),
                message.getSequenceNo(),
                message.getMessageType(),
                message.getStatus(),
                false
        );
    }
}
