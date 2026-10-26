package com.telme.chat.service;

public record ChatProcessingCommand(
        Long executionId,
        Long sessionId,
        Long inputMessageId,
        String content,
        ChatCoordinates coordinates,
        ChatResponseStopwatch stopwatch
) {

    public ChatProcessingCommand(Long executionId, Long sessionId, Long inputMessageId, String content) {
        this(executionId, sessionId, inputMessageId, content, null, null);
    }

    public ChatProcessingCommand(
            Long executionId, Long sessionId, Long inputMessageId, String content, ChatCoordinates coordinates) {
        this(executionId, sessionId, inputMessageId, content, coordinates, null);
    }

    @Override
    public String toString() {
        return "ChatProcessingCommand[executionId=%d, sessionId=%d, inputMessageId=%d]"
                .formatted(executionId, sessionId, inputMessageId);
    }
}
