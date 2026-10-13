package com.telme.subscription.converter;

import com.telme.plan.converter.PlanConverter;
import com.telme.plan.entity.Plan;
import com.telme.subscription.dto.res.SubscriptionResponse;
import com.telme.subscription.dto.res.TerminationPenaltyResponse;
import com.telme.subscription.entity.Subscription;
import com.telme.subscription.entity.SubscriptionPause;
import com.telme.subscription.service.ContractPenalty;
import com.telme.subscription.service.PausePolicy;
import com.telme.subscription.service.SubscriptionSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SubscriptionConverter {

    private final PlanConverter planConverter;

    public SubscriptionResponse toResponse(SubscriptionSnapshot snapshot) {
        // 개통 전에는 가입한 요금제의 월정액을 기준으로 할인액을 보여준다
        Plan basePlan = snapshot.currentPlan() != null ? snapshot.currentPlan() : snapshot.pendingActivation().plan();
        return SubscriptionResponse.builder()
                .subscriptionId(snapshot.subscription().getSubscriptionId())
                .subscribedAt(snapshot.subscription().getSubscribedAt())
                .currentPlan(snapshot.currentPlan() == null ? null : planConverter.toResponse(snapshot.currentPlan()))
                .contract(toContract(snapshot.subscription(), basePlan))
                .pendingActivation(toScheduled(snapshot.pendingActivation()))
                .pendingChange(toScheduled(snapshot.pendingChange()))
                .pause(toPause(snapshot.pause(), basePlan))
                .build();
    }

    public TerminationPenaltyResponse toPenaltyResponse(ContractPenalty penalty) {
        return new TerminationPenaltyResponse(penalty.contractMonths() != null, penalty.contractMonths(),
                penalty.elapsedMonths(), penalty.receivedDiscount(), penalty.refundPercent(), penalty.penalty());
    }

    private SubscriptionResponse.ContractResponse toContract(Subscription subscription, Plan basePlan) {
        if (!subscription.hasContract()) {
            return null;
        }
        int percent = subscription.getContractDiscountPercent();
        return new SubscriptionResponse.ContractResponse(
                subscription.getContractMonths(), percent, basePlan.getMonthlyFee() * percent / 100);
    }

    private SubscriptionResponse.PauseResponse toPause(SubscriptionPause pause, Plan basePlan) {
        return pause == null ? null : new SubscriptionResponse.PauseResponse(pause.getStartedAt(), pause.getEndsAt(),
                basePlan.getMonthlyFee() * PausePolicy.REDUCED_FEE_PERCENT / 100);
    }

    private SubscriptionResponse.ScheduledPlanResponse toScheduled(SubscriptionSnapshot.ScheduledPlan scheduled) {
        return scheduled == null ? null : new SubscriptionResponse.ScheduledPlanResponse(
                planConverter.toResponse(scheduled.plan()), scheduled.effectiveFrom());
    }
}
