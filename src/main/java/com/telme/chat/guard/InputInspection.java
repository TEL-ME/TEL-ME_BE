package com.telme.chat.guard;

import java.util.List;

/** 저장·상담 전달에 사용할 안전한 입력과 규칙 식별자만 보관한다. */
public record InputInspection(String content, List<Detection> detections, boolean hasQuestion) {
    public enum Reason {
        PROFANITY,
        INITIAL_PROFANITY,
        SENSITIVE_INFORMATION
    }

    public record Detection(Reason reason, String ruleId) {}

    public InputInspection {
        detections = List.copyOf(detections);
    }

    public boolean hasProfanity() {
        return detections.stream()
                .anyMatch(value -> value.reason() != Reason.SENSITIVE_INFORMATION);
    }

    public boolean wasMasked() {
        return detections.stream()
                .anyMatch(value -> value.reason() == Reason.SENSITIVE_INFORMATION);
    }

    @Override
    public String toString() {
        return "InputInspection[content=REDACTED, detections="
                + detections
                + ", hasQuestion="
                + hasQuestion
                + "]";
    }
}
