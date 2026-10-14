package com.telme.subscription.service;

import com.telme.plan.entity.Plan;
import com.telme.plan.repository.PlanRepository;
import com.telme.subscription.entity.Subscription;
import com.telme.subscription.entity.SubscriptionHistory;
import com.telme.subscription.repository.SubscriptionHistoryRepository;
import com.telme.subscription.repository.SubscriptionPauseRepository;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 판매 종료된 요금제에 가입 중일 수 있어 요금제는 상태와 무관하게 조회
@Component
@RequiredArgsConstructor
public class SubscriptionStateReader {

    private final SubscriptionHistoryRepository historyRepository;
    private final PlanRepository planRepository;
    private final SubscriptionPauseRepository pauseRepository;
    private final SubscriptionCalendar calendar;

    public SubscriptionSnapshot read(Subscription subscription, Instant now) {
        Long subscriptionId = subscription.getSubscriptionId();
        return new SubscriptionSnapshot(
                subscription,
                findCurrentPlan(subscriptionId, now).orElse(null),
                scheduled(historyRepository.findPendingActivation(subscriptionId, now)),
                scheduled(historyRepository.findPendingChange(subscriptionId, now)),
                pauseRepository.findActive(subscriptionId, now).orElse(null),
                contractInfo(subscription, now));
    }

    Optional<Plan> findCurrentPlan(Long subscriptionId, Instant now) {
        return historyRepository.findCurrent(subscriptionId, now)
                .map(history -> findPlan(history.getToPlanId()));
    }

    // 위약금과 같은 기준(가입일부터 만 개월)으로 약정 진행 여부를 판단하고, 만료일은 그 날 0시(KST)
    private SubscriptionSnapshot.ContractInfo contractInfo(Subscription subscription, Instant now) {
        if (!subscription.hasContract()) {
            return null;
        }
        int months = subscription.getContractMonths();
        boolean active = ContractPolicy.isActive(calendar.monthsBetween(subscription.getSubscribedAt(), now), months);
        Instant endsAt = calendar.startOfDay(calendar.plusMonths(subscription.getSubscribedAt(), months));
        return new SubscriptionSnapshot.ContractInfo(active, endsAt);
    }

    private SubscriptionSnapshot.ScheduledPlan scheduled(Optional<SubscriptionHistory> history) {
        return history.map(h -> new SubscriptionSnapshot.ScheduledPlan(findPlan(h.getToPlanId()), h.getEffectiveFrom()))
                .orElse(null);
    }

    private Plan findPlan(Long planId) {
        return planRepository.findById(planId)
                .orElseThrow(() -> new IllegalStateException("이력이 가리키는 요금제가 없습니다. planId=" + planId));
    }
}
