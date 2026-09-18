package com.telme.consult.dto;

import com.telme.consult.dto.DialogueInput.Condition;

import lombok.Builder;

import java.util.Map;
import java.util.Objects;

/** PROCEED는 검색 진행 허용이며, 최종 답변 완료가 아니다. */
@Builder
public record DialogueDecision(
        Long consultRequestId,
        Action action,
        Map<String, Condition> conditions,
        String waitingField,
        String message,
        MessageOrigin messageOrigin) {
    public enum Action {
        PROCEED,
        ASK,
        ALTERNATIVE_GUIDANCE
    }

    public enum MessageOrigin {
        NONE,
        TEMPLATE,
        MODEL
    }

    public DialogueDecision {
        if (consultRequestId == null || consultRequestId <= 0) {
            throw new IllegalArgumentException("상담 ID가 필요합니다.");
        }
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(messageOrigin, "messageOrigin");
        conditions = Map.copyOf(conditions);
        if (conditions.keySet().stream().anyMatch(key -> key.isBlank() || key.length() > 50)) {
            throw new IllegalArgumentException("조건 이름이 올바르지 않습니다.");
        }
        if (action == Action.ASK) {
            if (waitingField == null || waitingField.isBlank() || waitingField.length() > 50) {
                throw new IllegalArgumentException("되물을 조건이 필요합니다.");
            }
            var condition = conditions.get(waitingField);
            if (condition != null && condition.status() != DialogueInput.ConditionStatus.PENDING) {
                throw new IllegalArgumentException("이미 확인된 조건을 다시 물을 수 없습니다.");
            }
        } else if (waitingField != null) {
            throw new IllegalArgumentException("되묻기 외에는 대기 조건을 지정할 수 없습니다.");
        }
        if (action == Action.PROCEED) {
            if (message != null || messageOrigin != MessageOrigin.NONE) {
                throw new IllegalArgumentException("진행 판단에는 생성 문장을 넣지 않습니다.");
            }
        } else if (message == null || message.isBlank() || messageOrigin == MessageOrigin.NONE) {
            throw new IllegalArgumentException("안내 문장과 생성 출처가 필요합니다.");
        }
    }
}
