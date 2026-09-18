package com.telme.chat.service;

import com.telme.chat.entity.ChatExecution;
import com.telme.chat.entity.ChatMessage;
import java.util.Objects;

public record ChatFailure(ChatMessage.Status status, String errorCode) {

    private static final int ERROR_CODE_MAX_LENGTH = 50;

    public ChatFailure {
        Objects.requireNonNull(status, "status");
        if (status != ChatMessage.Status.FAILED
                && status != ChatMessage.Status.TIMEOUT
                && status != ChatMessage.Status.CANCELLED) {
            throw new IllegalArgumentException("실패 상태는 FAILED, TIMEOUT, CANCELLED만 가능합니다: " + status);
        }
        if (errorCode == null || errorCode.isBlank() || errorCode.length() > ERROR_CODE_MAX_LENGTH) {
            throw new IllegalArgumentException("errorCode는 1~50자여야 합니다.");
        }
    }

    ChatExecution.Status executionStatus() {
        return status == ChatMessage.Status.CANCELLED
                ? ChatExecution.Status.CANCELLED
                : ChatExecution.Status.FAILED;
    }
}
