package com.telme.subscription.dto.res;

import com.telme.plan.dto.res.PlanResponse;
import java.time.Instant;
import lombok.Builder;

// 개통 전에는 currentPlan이 null이고 pendingActivation에 개통 예정이 있다. pendingChange는 적용 전인 변경이고 없으면 null
@Builder
public record SubscriptionResponse(
        Long subscriptionId,
        Instant subscribedAt,
        PlanResponse currentPlan,
        ScheduledPlanResponse pendingActivation,
        ScheduledPlanResponse pendingChange
) {

    public record ScheduledPlanResponse(PlanResponse plan, Instant effectiveFrom) {
    }
}
