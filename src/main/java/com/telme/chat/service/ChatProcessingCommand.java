package com.telme.chat.service;

public record ChatProcessingCommand(
        Long executionId,
        Long sessionId,
        Long inputMessageId
) {
}
