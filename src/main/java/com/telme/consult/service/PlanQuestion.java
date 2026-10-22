package com.telme.consult.service;

import java.util.List;

/**
 * 요금제 질문 분류 결과. mention은 모델이 판매 중인 요금제 목록을 보고 질문이 가리키는 요금제를 고른 결과이고,
 * 규칙으로 분류했거나 요금제 목록 질문이 아니면 null이다(답변은 규칙으로 요금제 이름을 찾는다).
 */
public record PlanQuestion(PlanQuestionType type, Mention mention) {

    public static PlanQuestion of(PlanQuestionType type) {
        return new PlanQuestion(type, null);
    }

    /** planCodes: 고객이 이름으로 가리킨 판매 중 요금제. unknownPlan: 판매 중인 목록에 없는 요금제를 물음 */
    public record Mention(List<String> planCodes, boolean unknownPlan) {
        public Mention {
            planCodes = planCodes == null ? List.of() : List.copyOf(planCodes);
        }
    }
}
