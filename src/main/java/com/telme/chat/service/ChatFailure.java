package com.telme.chat.service;

import com.telme.chat.entity.ChatExecution;
import com.telme.chat.entity.ChatMessage;
import com.fasterxml.jackson.annotation.JsonProperty;
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

    /** 최초 오류와 종료 후 재구독에 같은 고정 안내를 제공한다. 내부 예외 내용은 포함하지 않는다. */
    @JsonProperty("message")
    public String message() {
        if (status == ChatMessage.Status.CANCELLED) {
            return "답변 생성을 취소했습니다.";
        }
        if (status == ChatMessage.Status.TIMEOUT
                || "LLM504-0".equals(errorCode) || "EXECUTION_TIMEOUT".equals(errorCode)) {
            return "답변 생성 시간이 초과되었습니다. 잠시 후 다시 질문해 주세요.";
        }
        return switch (errorCode) {
            case "FAQ_SEARCH_FAILED" -> "참고 정보를 검색하지 못했습니다. 잠시 후 다시 질문해 주세요.";
            case "LLM503-0" -> "답변 생성 서비스에 연결할 수 없습니다. 잠시 후 다시 질문해 주세요.";
            default -> "답변을 생성하지 못했습니다. 잠시 후 다시 질문해 주세요.";
        };
    }
}
