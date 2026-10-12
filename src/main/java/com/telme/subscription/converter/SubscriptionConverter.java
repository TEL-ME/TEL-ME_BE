package com.telme.subscription.converter;

import com.telme.plan.converter.PlanConverter;
import com.telme.subscription.dto.res.SubscriptionResponse;
import com.telme.subscription.service.SubscriptionSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SubscriptionConverter {

    private final PlanConverter planConverter;

    public SubscriptionResponse toResponse(SubscriptionSnapshot snapshot) {
        SubscriptionSnapshot.PendingChange pending = snapshot.pendingChange();
        return SubscriptionResponse.builder()
                .subscriptionId(snapshot.subscription().getSubscriptionId())
                .subscribedAt(snapshot.subscription().getSubscribedAt())
                .currentPlan(planConverter.toResponse(snapshot.currentPlan()))
                .pendingChange(pending == null ? null : new SubscriptionResponse.PendingChangeResponse(
                        planConverter.toResponse(pending.plan()), pending.effectiveFrom()))
                .build();
    }
}
