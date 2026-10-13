package com.telme.subscription.service;

import com.telme.plan.entity.Plan;
import com.telme.plan.repository.PlanRepository;
import com.telme.subscription.entity.Subscription;
import com.telme.subscription.entity.SubscriptionHistory;
import com.telme.subscription.repository.SubscriptionHistoryRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 받은 할인액 = 경과한 각 달의 월정액 × 할인율. 경과 개월은 가입일 기준 만 개월이고 각 달의 요금제는 그 달 시작 시점 기준
@Component
@RequiredArgsConstructor
public class ContractPenaltyCalculator {

    private final SubscriptionHistoryRepository historyRepository;
    private final PlanRepository planRepository;
    private final SubscriptionCalendar calendar;

    public ContractPenalty calculate(Subscription subscription, Instant now) {
        if (!subscription.hasContract()) {
            return ContractPenalty.none();
        }
        int contractMonths = subscription.getContractMonths();
        int discountPercent = subscription.getContractDiscountPercent();
        int elapsedMonths = Math.max(0, calendar.monthsBetween(subscription.getSubscribedAt(), now));
        int discountedMonths = Math.min(elapsedMonths, contractMonths);

        List<SubscriptionHistory> planHistories = historyRepository
                .findBySubscriptionIdOrderByEffectiveFromAscHistoryIdAsc(subscription.getSubscriptionId())
                .stream()
                .filter(history -> history.getType() != SubscriptionHistory.Type.CANCEL)
                .toList();
        Map<Long, Plan> plans = planRepository
                .findAllById(planHistories.stream().map(SubscriptionHistory::getToPlanId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Plan::getPlanId, Function.identity()));

        long receivedDiscount = 0;
        for (int month = 0; month < discountedMonths; month++) {
            Instant monthStart = calendar.plusMonths(subscription.getSubscribedAt(), month);
            Plan plan = plans.get(planAt(planHistories, monthStart));
            receivedDiscount += (long) plan.getMonthlyFee() * discountPercent / 100;
        }

        int refundPercent = ContractPolicy.refundPercent(elapsedMonths, contractMonths);
        return new ContractPenalty(contractMonths, elapsedMonths, receivedDiscount, refundPercent,
                receivedDiscount * refundPercent / 100);
    }

    // 개통 전인 첫 달에는 적용 중인 요금제가 없어 가입한 요금제로 본다
    private Long planAt(List<SubscriptionHistory> planHistories, Instant at) {
        Long planId = planHistories.get(0).getToPlanId();
        for (SubscriptionHistory history : planHistories) {
            if (history.getEffectiveFrom().isAfter(at)) {
                break;
            }
            planId = history.getToPlanId();
        }
        return planId;
    }
}
