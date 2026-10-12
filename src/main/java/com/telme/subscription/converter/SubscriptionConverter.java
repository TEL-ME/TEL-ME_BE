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
        return SubscriptionResponse.builder()
                .subscriptionId(snapshot.subscription().getSubscriptionId())
                .subscribedAt(snapshot.subscription().getSubscribedAt())
                .currentPlan(snapshot.currentPlan() == null ? null : planConverter.toResponse(snapshot.currentPlan()))
                .pendingActivation(toScheduled(snapshot.pendingActivation()))
                .pendingChange(toScheduled(snapshot.pendingChange()))
                .build();
    }

    private SubscriptionResponse.ScheduledPlanResponse toScheduled(SubscriptionSnapshot.ScheduledPlan scheduled) {
        return scheduled == null ? null : new SubscriptionResponse.ScheduledPlanResponse(
                planConverter.toResponse(scheduled.plan()), scheduled.effectiveFrom());
    }
}
