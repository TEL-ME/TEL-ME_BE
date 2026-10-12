package com.telme.subscription.dto.res;

import com.telme.plan.dto.res.PlanResponse;
import java.time.Instant;
import lombok.Builder;

// pendingChange는 아직 적용 전인 요금제 변경이고 없으면 null
@Builder
public record SubscriptionResponse(
        Long subscriptionId,
        Instant subscribedAt,
        PlanResponse currentPlan,
        PendingChangeResponse pendingChange
) {

    public record PendingChangeResponse(PlanResponse plan, Instant effectiveFrom) {
    }
}
