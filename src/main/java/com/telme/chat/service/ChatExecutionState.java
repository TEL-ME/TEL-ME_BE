package com.telme.chat.service;

import com.telme.chat.entity.ChatExecution;
import com.telme.chat.entity.ChatMessage;

public record ChatExecutionState(
        Long sessionId,
        Long executionId,
        ChatExecution.Status status,
        String errorCode,
        ChatOutputMessage outputMessage
) {

    static ChatExecutionState of(ChatExecution execution) {
        ChatMessage output = execution.getOutputMessage();
        return new ChatExecutionState(
                execution.getSession().getSessionId(),
                execution.getExecutionId(),
                execution.getStatus(),
                execution.getErrorCode(),
                output == null ? null : ChatOutputMessage.of(execution, output)
        );
    }
}
