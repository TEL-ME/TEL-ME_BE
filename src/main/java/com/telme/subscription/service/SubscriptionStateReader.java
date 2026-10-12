package com.telme.subscription.service;

import com.telme.plan.entity.Plan;
import com.telme.plan.repository.PlanRepository;
import com.telme.subscription.entity.Subscription;
import com.telme.subscription.entity.SubscriptionHistory;
import com.telme.subscription.repository.SubscriptionHistoryRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 판매 종료된 요금제에 가입 중일 수 있어 요금제는 상태와 무관하게 조회
@Component
@RequiredArgsConstructor
public class SubscriptionStateReader {

    private final SubscriptionHistoryRepository historyRepository;
    private final PlanRepository planRepository;

    public SubscriptionSnapshot read(Subscription subscription, Instant now) {
        Long subscriptionId = subscription.getSubscriptionId();
        Plan currentPlan = currentPlan(subscriptionId, now);
        SubscriptionSnapshot.PendingChange pending = historyRepository.findPendingChange(subscriptionId, now)
                .map(history -> new SubscriptionSnapshot.PendingChange(
                        findPlan(history.getToPlanId()), history.getEffectiveFrom()))
                .orElse(null);
        return new SubscriptionSnapshot(subscription, currentPlan, pending);
    }

    Plan currentPlan(Long subscriptionId, Instant now) {
        SubscriptionHistory current = historyRepository.findCurrent(subscriptionId, now)
                .orElseThrow(() -> new IllegalStateException("가입 이력이 없는 구독입니다. subscriptionId=" + subscriptionId));
        return findPlan(current.getToPlanId());
    }

    private Plan findPlan(Long planId) {
        return planRepository.findById(planId)
                .orElseThrow(() -> new IllegalStateException("이력이 가리키는 요금제가 없습니다. planId=" + planId));
    }
}
