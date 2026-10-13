package com.telme.chat.guard;

import java.util.List;

/**
 * 저장·상담에는 content를 사용한다. fingerprintContent는 재전송 비교 전용이며
 * 표시 문구가 바뀌어도 기존 유형 대체 문구를 유지한다. 두 값 모두 감지한 번호를 포함하지 않는다.
 */
public record InputInspection(
        String content,
        List<Detection> detections,
        boolean hasQuestion,
        String fingerprintContent) {

    public InputInspection(String content, List<Detection> detections, boolean hasQuestion) {
        this(content, detections, hasQuestion, content);
    }

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
