package com.telme.subscription.service;

import com.telme.subscription.entity.SubscriptionHistory;
import java.time.Instant;
import java.util.List;

// 구독 이력으로 특정 시점의 요금제를 찾는다. 위약금과 월별 요금 계산이 같은 기준을 쓴다
public final class PlanTimeline {

    private PlanTimeline() {
    }

    public static List<SubscriptionHistory> planHistories(List<SubscriptionHistory> histories) {
        return histories.stream()
                .filter(history -> history.getType() != SubscriptionHistory.Type.CANCEL)
                .toList();
    }

    // 개통 전인 첫 달에는 적용 중인 요금제가 없어 가입한 요금제로 본다
    public static Long planAt(List<SubscriptionHistory> planHistories, Instant at) {
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
