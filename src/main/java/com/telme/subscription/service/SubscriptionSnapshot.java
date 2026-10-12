package com.telme.subscription.service;

import com.telme.plan.entity.Plan;
import com.telme.subscription.entity.Subscription;
import java.time.Instant;

// 예약된 변경이 없으면 pendingChange는 null
public record SubscriptionSnapshot(Subscription subscription, Plan currentPlan, PendingChange pendingChange) {

    public record PendingChange(Plan plan, Instant effectiveFrom) {
    }
}
