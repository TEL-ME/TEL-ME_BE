package com.telme.usage.service;

import com.telme.global.common.TimeZones;
import com.telme.plan.entity.Plan;
import com.telme.plan.repository.PlanRepository;
import com.telme.subscription.entity.Subscription;
import com.telme.subscription.entity.SubscriptionHistory;
import com.telme.subscription.repository.SubscriptionHistoryRepository;
import com.telme.subscription.repository.SubscriptionRepository;
import com.telme.subscription.service.ContractPolicy;
import com.telme.subscription.service.PlanTimeline;
import com.telme.subscription.service.SubscriptionCalendar;
import com.telme.subscription.service.SubscriptionSnapshot;
import com.telme.subscription.service.SubscriptionStateReader;
import com.telme.usage.entity.SubscriptionUsage;
import com.telme.usage.repository.SubscriptionUsageRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 개통 이후의 달만 사용량을 만들고, 한 번 만든 값은 저장해 그대로 쓴다. 조회하면서 만드므로 읽기 전용 트랜잭션이 아니다
@Service
@RequiredArgsConstructor
@Transactional
public class SubscriptionUsageService {

    static final int PAST_MONTHS = 3;
    private static final int MB_PER_GB = 1024;

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionHistoryRepository historyRepository;
    private final SubscriptionUsageRepository usageRepository;
    private final PlanRepository planRepository;
    private final SubscriptionStateReader stateReader;
    private final SubscriptionCalendar calendar;
    private final UsageGenerator generator;

    public UsageOverview getOverview(Long userId) {
        Instant now = calendar.now();
        Optional<Subscription> found = subscriptionRepository.findByUserIdAndStatus(userId, Subscription.Status.ACTIVE);
        if (found.isEmpty()) {
            return UsageOverview.notSubscribed();
        }
        Subscription subscription = found.get();
        SubscriptionSnapshot snapshot = stateReader.read(subscription, now);
        if (snapshot.currentPlan() == null) {
            return UsageOverview.pendingActivation(snapshot.pendingActivation().effectiveFrom());
        }

        List<SubscriptionHistory> planHistories = planHistories(subscription);
        LocalDate activationDate = activationDate(planHistories);
        LocalDate today = calendar.today(now);
        LocalDate thisMonth = today.withDayOfMonth(1);
        SubscriptionUsage stored = ensureGenerated(subscription, activationDate, thisMonth).get(thisMonth);

        double fraction = visibleFraction(thisMonth, activationDate, today);
        Plan plan = snapshot.currentPlan();
        return UsageOverview.active(new ThisMonthUsage(
                thisMonth,
                thisMonth.lengthOfMonth() - today.getDayOfMonth(),
                snapshot.pause() != null,
                ResourceUsage.of(scale(stored.getUsedDataMb(), fraction), dataLimitMb(plan)),
                ResourceUsage.of(scale(stored.getUsedVoiceMinutes(), fraction), voiceLimit(plan)),
                ResourceUsage.of(scale(stored.getUsedSmsCount(), fraction), smsLimit(plan)),
                expectedFee(subscription, plan, planHistories, now, thisMonth, activationDate)));
    }

    // 이번 달을 뺀 최근 지난 달들(오래된 순). 추천에 쓰는 값이라 개통한 달은 개통 이후 일수만큼만 반영한다
    public List<MonthlyUsage> getRecentMonths(Long userId, int months) {
        if (months <= 0) {
            return List.of();
        }
        Instant now = calendar.now();
        Optional<Subscription> found = subscriptionRepository.findByUserIdAndStatus(userId, Subscription.Status.ACTIVE);
        if (found.isEmpty() || stateReader.read(found.get(), now).currentPlan() == null) {
            return List.of();
        }
        Subscription subscription = found.get();
        LocalDate activationDate = activationDate(planHistories(subscription));
        LocalDate today = calendar.today(now);
        LocalDate thisMonth = today.withDayOfMonth(1);

        List<SubscriptionUsage> past = ensureGenerated(subscription, activationDate, thisMonth).values().stream()
                .filter(usage -> usage.getUsageMonth().isBefore(thisMonth))
                .sorted((a, b) -> a.getUsageMonth().compareTo(b.getUsageMonth()))
                .toList();
        List<MonthlyUsage> result = new ArrayList<>();
        for (SubscriptionUsage usage : past.subList(Math.max(0, past.size() - Math.min(months, PAST_MONTHS)), past.size())) {
            double fraction = visibleFraction(usage.getUsageMonth(), activationDate, today);
            result.add(new MonthlyUsage(usage.getUsageMonth(), scale(usage.getUsedDataMb(), fraction),
                    (int) scale(usage.getUsedVoiceMinutes(), fraction), (int) scale(usage.getUsedSmsCount(), fraction)));
        }
        return result;
    }

    // 개통한 달부터 이번 달까지(최대 지난 3개월 + 이번 달) 없는 달만 만든다. 동시 요청이 같은 달을 만들어도 먼저 들어간 값이 남는다
    private Map<LocalDate, SubscriptionUsage> ensureGenerated(
            Subscription subscription, LocalDate activationDate, LocalDate thisMonth) {
        Long subscriptionId = subscription.getSubscriptionId();
        LocalDate activationMonth = activationDate.withDayOfMonth(1);
        LocalDate first = activationMonth.isAfter(thisMonth.minusMonths(PAST_MONTHS))
                ? activationMonth : thisMonth.minusMonths(PAST_MONTHS);

        Map<LocalDate, SubscriptionUsage> stored = byMonth(usageRepository.findFromMonth(subscriptionId, first));
        boolean inserted = false;
        for (LocalDate month = first; !month.isAfter(thisMonth); month = month.plusMonths(1)) {
            if (!stored.containsKey(month)) {
                GeneratedUsage generated = generator.generate(subscription.getUserId(), month);
                usageRepository.insertIfAbsent(subscriptionId, month,
                        generated.dataMb(), generated.voiceMinutes(), generated.smsCount());
                inserted = true;
            }
        }
        return inserted ? byMonth(usageRepository.findFromMonth(subscriptionId, first)) : stored;
    }

    private Map<LocalDate, SubscriptionUsage> byMonth(List<SubscriptionUsage> usages) {
        return usages.stream().collect(Collectors.toMap(SubscriptionUsage::getUsageMonth, Function.identity()));
    }

    // 해당 달 중 개통 이후이면서 오늘까지 지난 일수 / 그 달의 일수
    private double visibleFraction(LocalDate month, LocalDate activationDate, LocalDate today) {
        LocalDate from = activationDate.isAfter(month) ? activationDate : month;
        LocalDate monthEnd = month.withDayOfMonth(month.lengthOfMonth());
        LocalDate to = today.isBefore(monthEnd) ? today : monthEnd;
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        return days <= 0 ? 0 : (double) days / month.lengthOfMonth();
    }

    private long scale(long value, double fraction) {
        return Math.round(value * fraction);
    }

    private ExpectedFee expectedFee(Subscription subscription, Plan currentPlan, List<SubscriptionHistory> planHistories,
            Instant now, LocalDate thisMonth, LocalDate activationDate) {
        int amount = feeAfterContract(subscription, currentPlan.getMonthlyFee(), now);
        if (!activationDate.isBefore(thisMonth)) {
            return new ExpectedFee(amount, null, null);
        }
        Instant previousMonthStart = thisMonth.minusMonths(1).atStartOfDay(TimeZones.KST).toInstant();
        Long previousPlanId = PlanTimeline.planAt(planHistories, previousMonthStart);
        Plan previousPlan = planRepository.findById(previousPlanId).orElseThrow();
        int previousAmount = feeAfterContract(subscription, previousPlan.getMonthlyFee(), previousMonthStart);
        return new ExpectedFee(amount, previousAmount, amount - previousAmount);
    }

    private int feeAfterContract(Subscription subscription, int monthlyFee, Instant at) {
        if (!subscription.hasContract()
                || !ContractPolicy.isActive(calendar.monthsBetween(subscription.getSubscribedAt(), at),
                        subscription.getContractMonths())) {
            return monthlyFee;
        }
        return monthlyFee - monthlyFee * subscription.getContractDiscountPercent() / 100;
    }

    private List<SubscriptionHistory> planHistories(Subscription subscription) {
        return PlanTimeline.planHistories(historyRepository
                .findBySubscriptionIdOrderByEffectiveFromAscHistoryIdAsc(subscription.getSubscriptionId()));
    }

    // 첫 가입 이력의 적용 시작이 개통 시각이다
    private LocalDate activationDate(List<SubscriptionHistory> planHistories) {
        return calendar.today(planHistories.get(0).getEffectiveFrom());
    }

    private Long dataLimitMb(Plan plan) {
        return plan.isUnlimited() ? null
                : plan.getDataGb().multiply(BigDecimal.valueOf(MB_PER_GB)).setScale(0, RoundingMode.HALF_UP).longValue();
    }

    private Long voiceLimit(Plan plan) {
        return plan.isVoiceUnlimited() ? null : Long.valueOf(plan.getVoiceMinutes());
    }

    private Long smsLimit(Plan plan) {
        return plan.isSmsUnlimited() ? null : Long.valueOf(plan.getSmsCount());
    }
}
