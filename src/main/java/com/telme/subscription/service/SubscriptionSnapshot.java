package com.telme.subscription.service;

import com.telme.plan.entity.Plan;
import com.telme.subscription.entity.Subscription;
import com.telme.subscription.entity.SubscriptionPause;
import java.time.Instant;

// 개통 전이면 currentPlan이 null이고 pendingActivation에 개통 예정. 해당 없는 항목은 null
public record SubscriptionSnapshot(
        Subscription subscription,
        Plan currentPlan,
        ScheduledPlan pendingActivation,
        ScheduledPlan pendingChange,
        SubscriptionPause pause
) {

    public record ScheduledPlan(Plan plan, Instant effectiveFrom) {
    }
}
