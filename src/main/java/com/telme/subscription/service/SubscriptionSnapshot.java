package com.telme.subscription.service;

import com.telme.plan.entity.Plan;
import com.telme.subscription.entity.Subscription;
import java.time.Instant;

// 개통 전에는 currentPlan이 null이고 pendingActivation에 개통 예정이 있다. 예약된 변경이 없으면 pendingChange는 null
public record SubscriptionSnapshot(
        Subscription subscription,
        Plan currentPlan,
        ScheduledPlan pendingActivation,
        ScheduledPlan pendingChange
) {

    public record ScheduledPlan(Plan plan, Instant effectiveFrom) {
    }
}
