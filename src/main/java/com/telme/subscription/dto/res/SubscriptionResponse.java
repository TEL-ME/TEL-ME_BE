package com.telme.subscription.dto.res;

import com.telme.plan.dto.res.PlanResponse;
import java.time.Instant;
import lombok.Builder;

// 개통 전이면 currentPlan이 null이고 pendingActivation에 개통 예정이 있다. 해당 없는 항목(contract, pendingChange, pause)은 null
@Builder
public record SubscriptionResponse(
        Long subscriptionId,
        Instant subscribedAt,
        PlanResponse currentPlan,
        ContractResponse contract,
        ScheduledPlanResponse pendingActivation,
        ScheduledPlanResponse pendingChange,
        PauseResponse pause
) {

    public record ScheduledPlanResponse(PlanResponse plan, Instant effectiveFrom) {
    }

    // monthlyDiscount는 월정액에서 할인되는 금액(원)이고 약정이 만료되면(active=false) 0. endsAt은 약정 만료일
    public record ContractResponse(int months, int discountPercent, int monthlyDiscount, boolean active, Instant endsAt) {
    }

    // reducedMonthlyFee는 정지 기간에 부과되는 기본료(원)
    public record PauseResponse(Instant startedAt, Instant endsAt, int reducedMonthlyFee) {
    }
}
