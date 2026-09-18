package com.telme.chat.service;

public record ChatProcessingCommand(
        Long executionId,
        Long sessionId,
        Long inputMessageId,
        String content
) {

    @Override
    public String toString() {
        return "ChatProcessingCommand[executionId=%d, sessionId=%d, inputMessageId=%d]"
                .formatted(executionId, sessionId, inputMessageId);
    }
}
