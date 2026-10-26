package com.telme.consult.dto;

import com.telme.consult.dto.DialogueInput.Condition;
import com.telme.consult.dto.DialogueInput.ConditionStatus;

import java.util.List;
import java.util.Map;

/** 검색 근거로 정한 되묻기 계획. 조건이 없으면 되묻지 않고 바로 답변한다. */
public record ClarificationPlan(List<MissingCondition> conditions) {

    private static final ClarificationPlan NONE = new ClarificationPlan(List.of());

    public ClarificationPlan {
        conditions = List.copyOf(conditions);
    }

    public static ClarificationPlan none() {
        return NONE;
    }

    public boolean needsClarification() {
        return !conditions.isEmpty();
    }

    /** 이미 답을 받은 조건을 뺀 계획. 되묻기에 답하면 같은 조건을 다시 묻지 않는다. */
    public ClarificationPlan remaining(Map<String, Condition> known) {
        if (known == null || known.isEmpty()) {
            return this;
        }
        var left = conditions.stream()
                .filter(condition -> {
                    Condition answered = known.get(condition.key());
                    return answered == null || answered.status() == ConditionStatus.PENDING;
                })
                .toList();
        return left.isEmpty() ? none() : new ClarificationPlan(left);
    }

    /** 먼저 물을 조건. 한 번에 하나씩 묻는다. */
    public MissingCondition first() {
        if (conditions.isEmpty()) {
            throw new IllegalStateException("되물을 조건이 없습니다.");
        }
        return conditions.getFirst();
    }
}
