package com.telme.consult.service;

import com.telme.plan.entity.Plan;
import java.util.List;

/**
 * 요금제 질문 분류 결과. mention은 모델이 판매 중인 요금제 목록을 보고 질문이 가리키는 요금제를 고른 결과이고,
 * 규칙으로 분류했거나 요금제 목록 질문이 아니면 null이다(답변은 규칙으로 요금제 이름을 찾는다).
 */
public record PlanQuestion(PlanQuestionType type, Mention mention) {

    public static PlanQuestion of(PlanQuestionType type) {
        return new PlanQuestion(type, null);
    }

    /**
     * planCodes: 고객이 이름으로 가리킨 판매 중 요금제. unknownPlan: 판매 중인 목록에 없는 요금제를 물음.
     * conditions: 요금제를 고르지 않은 요금제 표 질문(목록, 개수, 묶음의 가격)에서 모델이 따로 읽은 조건. 없으면(호출 실패, 응답을 못 읽음) 답변이 질문의 낱말로 조건을 읽는다
     */
    public record Mention(List<String> planCodes, boolean unknownPlan, Conditions conditions) {
        public Mention {
            planCodes = planCodes == null ? List.of() : List.copyOf(planCodes);
        }

        public Mention(List<String> planCodes, boolean unknownPlan) {
            this(planCodes, unknownPlan, null);
        }
    }

    /**
     * 고객이 고른 망·대상과 뺀 망·대상·요금제, 무제한 요금제를 원하는지(ONLY), 빼는지(EXCLUDE), 상관없는지(ANY).
     * 이유로만 꺼낸 말("5G는 안 터지는 동네라서")은 조건이 아니다(TELME-227)
     */
    public record Conditions(List<Plan.NetworkType> networks, List<Plan.NetworkType> excludedNetworks,
            List<Plan.TargetGroup> targetGroups, List<Plan.TargetGroup> excludedTargetGroups, Unlimited unlimited,
            List<String> excludedPlans) {
        public Conditions {
            networks = networks == null ? List.of() : List.copyOf(networks);
            excludedNetworks = excludedNetworks == null ? List.of() : List.copyOf(excludedNetworks);
            targetGroups = targetGroups == null ? List.of() : List.copyOf(targetGroups);
            excludedTargetGroups = excludedTargetGroups == null ? List.of() : List.copyOf(excludedTargetGroups);
            unlimited = unlimited == null ? Unlimited.ANY : unlimited;
            excludedPlans = excludedPlans == null ? List.of() : List.copyOf(excludedPlans);
        }
    }

    public enum Unlimited { ONLY, EXCLUDE, ANY }
}
