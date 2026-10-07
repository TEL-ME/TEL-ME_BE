package com.telme.consult.service;

import com.telme.consult.dto.ClarificationPlan;
import com.telme.consult.dto.DialogueDecision;
import com.telme.consult.dto.DialogueDecision.Action;
import com.telme.consult.dto.DialogueDecision.MessageOrigin;
import com.telme.consult.dto.DialogueInput.Condition;
import com.telme.consult.dto.DialogueInput.ConditionStatus;
import com.telme.consult.dto.MissingCondition;
import java.util.HashMap;
import java.util.Map;

/** 되묻기 계획을 기존 상담 판단으로 옮긴다. 저장과 대기 상태는 매장 되묻기와 같은 경로를 쓴다. */
public final class FaqClarificationDecisions {

    private FaqClarificationDecisions() {}

    public static DialogueDecision ask(
            long consultRequestId, ClarificationPlan plan, Map<String, Condition> previous) {
        MissingCondition asked = plan.first();
        Map<String, Condition> conditions = new HashMap<>(previous);
        // 뽑은 조건을 모두 남긴다. 먼저 묻는 것 외에도 다음에 물을 것이 남아 있어야 한다
        for (MissingCondition condition : plan.conditions()) {
            conditions.putIfAbsent(condition.key(), new Condition(ConditionStatus.PENDING, null));
        }
        return new DialogueDecision(
                consultRequestId,
                Action.ASK,
                conditions,
                asked.key(),
                asked.question(),
                MessageOrigin.TEMPLATE);
    }
}
