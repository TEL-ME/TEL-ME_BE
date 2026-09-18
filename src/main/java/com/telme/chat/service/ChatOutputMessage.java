package com.telme.chat.service;

import com.telme.chat.entity.ChatExecution;
import com.telme.chat.entity.ChatMessage;

public record ChatOutputMessage(
        Long sessionId,
        Long executionId,
        Long messageId,
        Integer sequenceNo,
        ChatMessage.MessageType messageType,
        ChatMessage.Status status
) {

    static ChatOutputMessage of(ChatExecution execution, ChatMessage message) {
        return new ChatOutputMessage(
                message.getSession().getSessionId(),
                execution.getExecutionId(),
                message.getMessageId(),
                message.getSequenceNo(),
                message.getMessageType(),
                message.getStatus()
        );
    }
}
