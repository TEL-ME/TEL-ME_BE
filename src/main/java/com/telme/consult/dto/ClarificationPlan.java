package com.telme.consult.dto;

import java.util.List;

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

    /** 먼저 물을 조건. 한 번에 하나씩 묻는다. */
    public MissingCondition first() {
        if (conditions.isEmpty()) {
            throw new IllegalStateException("되물을 조건이 없습니다.");
        }
        return conditions.getFirst();
    }
}
