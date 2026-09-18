package com.telme.consult.dto;

import lombok.Builder;

import java.util.Map;
import java.util.Objects;

/** HTTP 요청 DTO가 아닌 내부 입력 모델이다. 복합 질문은 상담 요청별로 나눠서 전달한다. */
@Builder
public record DialogueInput(
        Long consultRequestId,
        Purpose purpose,
        Map<String, Condition> previousConditions,
        Map<String, Condition> updates,
        LocationStatus locationStatus) {

    public enum Purpose {
        GENERAL_FAQ,
        NEARBY_STORE
    }

    public enum LocationStatus {
        AVAILABLE,
        MISSING,
        DECLINED
    }

    public enum ConditionStatus {
        FILLED,
        DECLINED,
        PENDING
    }

    public record Condition(ConditionStatus status, String value) {
        public Condition {
            Objects.requireNonNull(status, "status");
            if (status == ConditionStatus.FILLED) {
                if (value == null || value.isBlank()) {
                    throw new IllegalArgumentException("FILLED condition requires a value");
                }
                value = value.strip();
                if (value.length() > 255) {
                    throw new IllegalArgumentException("Condition too long");
                }
            } else if (value != null) {
                throw new IllegalArgumentException("Only FILLED condition can have a value");
            }
        }

        public static Condition filled(String value) {
            return new Condition(ConditionStatus.FILLED, value);
        }

        public static Condition declined() {
            return new Condition(ConditionStatus.DECLINED, null);
        }

        public static Condition pending() {
            return new Condition(ConditionStatus.PENDING, null);
        }
    }

    public DialogueInput {
        if (consultRequestId == null || consultRequestId <= 0) {
            throw new IllegalArgumentException("consultRequestId must be positive");
        }
        Objects.requireNonNull(purpose, "purpose");
        Objects.requireNonNull(locationStatus, "locationStatus");
        previousConditions = copyConditions(previousConditions);
        updates = copyConditions(updates);
    }

    private static Map<String, Condition> copyConditions(Map<String, Condition> conditions) {
        var copy = Map.copyOf(Objects.requireNonNull(conditions, "conditions"));
        if (copy.keySet().stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("Condition key must not be blank");
        }
        return copy;
    }
}
