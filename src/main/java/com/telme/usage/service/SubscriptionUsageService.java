package com.telme.usage.service;

import com.telme.global.common.TimeZones;
import com.telme.global.common.exception.GeneralException;
import com.telme.plan.entity.Plan;
import com.telme.plan.repository.PlanRepository;
import com.telme.subscription.entity.Subscription;
import com.telme.subscription.entity.SubscriptionHistory;
import com.telme.subscription.entity.SubscriptionPause;
import com.telme.subscription.repository.SubscriptionHistoryRepository;
import com.telme.subscription.repository.SubscriptionPauseRepository;
import com.telme.subscription.repository.SubscriptionRepository;
import com.telme.subscription.service.ContractPolicy;
import com.telme.subscription.service.PlanTimeline;
import com.telme.subscription.service.SubscriptionCalendar;
import com.telme.subscription.service.SubscriptionSnapshot;
import com.telme.subscription.service.SubscriptionStateReader;
import com.telme.usage.entity.SubscriptionUsage;
import com.telme.usage.exception.UsageErrorCode;
import com.telme.usage.repository.SubscriptionUsageRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

    static final int VIEWABLE_PAST_MONTHS = 6;
    static final int RECENT_MONTHS_LIMIT = 3;
    static final int DEMAND_MONTHS = 3;

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionHistoryRepository historyRepository;
    private final SubscriptionPauseRepository pauseRepository;
    private final SubscriptionUsageRepository usageRepository;
    private final PlanRepository planRepository;
    private final SubscriptionStateReader stateReader;
    private final SubscriptionCalendar calendar;
    private final UsageGenerator generator;

    public UsageOverview getOverview(Long userId) {
        return getOverview(userId, null);
    }

    // month가 null이면 이번 달. 개통한 달 이후이면서 6개월 전까지만 조회할 수 있고 범위 밖이면 예외
    public UsageOverview getOverview(Long userId, YearMonth month) {
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
        LocalDate availableFrom = firstMonth(activationDate, thisMonth);
        LocalDate target = month == null ? thisMonth : month.atDay(1);
        if (target.isBefore(availableFrom) || target.isAfter(thisMonth)) {
            throw new GeneralException(UsageErrorCode.MONTH_OUT_OF_RANGE);
        }

        SubscriptionUsage stored = ensureGenerated(subscription, planHistories, activationDate, thisMonth).get(target);
        double fraction = visibleFraction(target, activationDate, today,
                pausedDates(subscription.getSubscriptionId(), availableFrom, today));
        MonthUsage usage = target.equals(thisMonth)
                ? thisMonthUsage(subscription, snapshot, planHistories, stored, fraction, today, activationDate)
                : pastMonthUsage(stored, fraction, limitsAt(planHistories, target));
        return UsageOverview.active(availableFrom, thisMonth, usage);
    }

    private MonthUsage thisMonthUsage(Subscription subscription, SubscriptionSnapshot snapshot,
            List<SubscriptionHistory> planHistories, SubscriptionUsage stored, double fraction,
            LocalDate today, LocalDate activationDate) {
        LocalDate thisMonth = today.withDayOfMonth(1);
        UsageLimits limits = limitsAt(planHistories, thisMonth);
        return new MonthUsage(
                thisMonth,
                thisMonth.lengthOfMonth() - today.getDayOfMonth(),
                snapshot.pause() != null,
                ResourceUsage.of(scale(stored.getUsedDataMb(), fraction), limits.dataMb()),
                ResourceUsage.of(scale(stored.getUsedVoiceMinutes(), fraction), limits.voiceMinutes()),
                ResourceUsage.of(scale(stored.getUsedSmsCount(), fraction), limits.smsCount()),
                expectedFee(subscription, planHistories, thisMonth, activationDate));
    }

    private MonthUsage pastMonthUsage(SubscriptionUsage stored, double fraction, UsageLimits limits) {
        return new MonthUsage(
                stored.getUsageMonth(), null, null,
                ResourceUsage.of(scale(stored.getUsedDataMb(), fraction), limits.dataMb()),
                ResourceUsage.of(scale(stored.getUsedVoiceMinutes(), fraction), limits.voiceMinutes()),
                ResourceUsage.of(scale(stored.getUsedSmsCount(), fraction), limits.smsCount()),
                null);
    }

    // 이번 달을 뺀 최근 지난 달들(오래된 순). 추천에 쓰는 값이라 개통 이전과 정지한 날은 반영하지 않는다
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
        List<SubscriptionHistory> planHistories = planHistories(subscription);
        LocalDate activationDate = activationDate(planHistories);
        LocalDate today = calendar.today(now);
        LocalDate thisMonth = today.withDayOfMonth(1);

        Set<LocalDate> pausedDates = pausedDates(
                subscription.getSubscriptionId(), firstMonth(activationDate, thisMonth), today);
        List<SubscriptionUsage> past = ensureGenerated(subscription, planHistories, activationDate, thisMonth).values().stream()
                .filter(usage -> usage.getUsageMonth().isBefore(thisMonth))
                .sorted((a, b) -> a.getUsageMonth().compareTo(b.getUsageMonth()))
                .toList();
        List<MonthlyUsage> result = new ArrayList<>();
        for (SubscriptionUsage usage : past.subList(Math.max(0, past.size() - Math.min(months, RECENT_MONTHS_LIMIT)), past.size())) {
            double fraction = visibleFraction(usage.getUsageMonth(), activationDate, today, pausedDates);
            result.add(new MonthlyUsage(usage.getUsageMonth(), scale(usage.getUsedDataMb(), fraction),
                    (int) scale(usage.getUsedVoiceMinutes(), fraction), (int) scale(usage.getUsedSmsCount(), fraction)));
        }
        return result;
    }

    // 추천에 쓰는 평소 사용량. 이번 달을 포함한 최근 3개월의 한 달 전체 값의 평균이고 개통 전 달은 뺀다
    public Optional<UsageDemand> getDemand(Long userId) {
        Instant now = calendar.now();
        Optional<Subscription> found = subscriptionRepository.findByUserIdAndStatus(userId, Subscription.Status.ACTIVE);
        if (found.isEmpty() || stateReader.read(found.get(), now).currentPlan() == null) {
            return Optional.empty();
        }
        Subscription subscription = found.get();
        List<SubscriptionHistory> planHistories = planHistories(subscription);
        LocalDate activationDate = activationDate(planHistories);
        LocalDate thisMonth = calendar.today(now).withDayOfMonth(1);
        LocalDate from = thisMonth.minusMonths(DEMAND_MONTHS - 1L);
        List<SubscriptionUsage> months = ensureGenerated(subscription, planHistories, activationDate, thisMonth).values()
                .stream()
                .filter(usage -> !usage.getUsageMonth().isBefore(from))
                .toList();
        if (months.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new UsageDemand(
                Math.round(months.stream().mapToLong(SubscriptionUsage::getUsedDataMb).average().orElse(0)),
                Math.round(months.stream().mapToLong(SubscriptionUsage::getUsedVoiceMinutes).average().orElse(0)),
                Math.round(months.stream().mapToLong(SubscriptionUsage::getUsedSmsCount).average().orElse(0)),
                months.size()));
    }

    // 개통한 달부터 이번 달까지(최대 6개월 전부터) 없는 달만 그 달 1일의 요금제 제공량 기준으로 만든다. 동시 요청은 먼저 들어간 값이 남는다
    private Map<LocalDate, SubscriptionUsage> ensureGenerated(Subscription subscription,
            List<SubscriptionHistory> planHistories, LocalDate activationDate, LocalDate thisMonth) {
        Long subscriptionId = subscription.getSubscriptionId();
        LocalDate first = firstMonth(activationDate, thisMonth);

        Map<LocalDate, SubscriptionUsage> stored = byMonth(usageRepository.findFromMonth(subscriptionId, first));
        boolean inserted = false;
        for (LocalDate month = first; !month.isAfter(thisMonth); month = month.plusMonths(1)) {
            if (!stored.containsKey(month)) {
                GeneratedUsage generated = generator.generate(subscription.getUserId(), month,
                        habitLimits(planHistories), limitsAt(planHistories, month));
                usageRepository.insertIfAbsent(subscriptionId, month,
                        generated.dataMb(), generated.voiceMinutes(), generated.smsCount());
                inserted = true;
            }
        }
        return inserted ? byMonth(usageRepository.findFromMonth(subscriptionId, first)) : stored;
    }

    // 사용 습관의 기준인 가입 당시 요금제의 제공량
    private UsageLimits habitLimits(List<SubscriptionHistory> planHistories) {
        return UsageLimits.of(planRepository.findById(planHistories.get(0).getToPlanId()).orElseThrow());
    }

    // 사용량 생성과 한도 표시는 그 달 1일에 쓰던 요금제의 제공량 기준
    private UsageLimits limitsAt(List<SubscriptionHistory> planHistories, LocalDate month) {
        return UsageLimits.of(planRepository.findById(PlanTimeline.planAt(planHistories, startOfDay(month)))
                .orElseThrow());
    }

    // 개통한 달과 6개월 전 중 늦은 달
    private LocalDate firstMonth(LocalDate activationDate, LocalDate thisMonth) {
        LocalDate activationMonth = activationDate.withDayOfMonth(1);
        LocalDate oldestViewable = thisMonth.minusMonths(VIEWABLE_PAST_MONTHS);
        return activationMonth.isAfter(oldestViewable) ? activationMonth : oldestViewable;
    }

    private Instant startOfDay(LocalDate day) {
        return day.atStartOfDay(TimeZones.KST).toInstant();
    }

    private Map<LocalDate, SubscriptionUsage> byMonth(List<SubscriptionUsage> usages) {
        return usages.stream().collect(Collectors.toMap(SubscriptionUsage::getUsageMonth, Function.identity()));
    }

    // 해당 달 중 개통 이후이면서 오늘까지 지난 일수에서 정지한 날을 뺀 값 / 그 달의 일수
    private double visibleFraction(LocalDate month, LocalDate activationDate, LocalDate today,
            Set<LocalDate> pausedDates) {
        LocalDate from = activationDate.isAfter(month) ? activationDate : month;
        LocalDate monthEnd = month.withDayOfMonth(month.lengthOfMonth());
        LocalDate to = today.isBefore(monthEnd) ? today : monthEnd;
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (days <= 0) {
            return 0;
        }
        long paused = from.datesUntil(to.plusDays(1)).filter(pausedDates::contains).count();
        return (double) (days - paused) / month.lengthOfMonth();
    }

    // 정지를 신청한 날과 복귀한 날은 이용한 날로 보고 그 사이 날만 정지한 날로 센다
    private Set<LocalDate> pausedDates(Long subscriptionId, LocalDate from, LocalDate to) {
        Instant fromAt = from.atStartOfDay(TimeZones.KST).toInstant();
        Instant toAt = to.plusDays(1).atStartOfDay(TimeZones.KST).toInstant();
        Set<LocalDate> dates = new HashSet<>();
        for (SubscriptionPause pause : pauseRepository.findOverlapping(subscriptionId, fromAt, toAt)) {
            LocalDate endDate = calendar.today(pause.getEndsAt());
            for (LocalDate date = calendar.today(pause.getStartedAt()).plusDays(1); date.isBefore(endDate);
                    date = date.plusDays(1)) {
                dates.add(date);
            }
        }
        return dates;
    }

    private long scale(long value, double fraction) {
        return Math.round(value * fraction);
    }

    private ExpectedFee expectedFee(Subscription subscription, List<SubscriptionHistory> planHistories,
            LocalDate thisMonth, LocalDate activationDate) {
        MonthlyFee current = monthlyFee(subscription, planHistories, thisMonth, activationDate);
        if (!activationDate.isBefore(thisMonth)) {
            return new ExpectedFee(current.amount(), null, null, current.prorated());
        }
        LocalDate previousMonth = thisMonth.minusMonths(1);
        int previous = monthlyFee(subscription, planHistories, previousMonth, activationDate).amount();
        return new ExpectedFee(current.amount(), previous, current.amount() - previous, current.prorated());
    }

    // 날마다 그날 적용 중인 요금제의 월정액에 그날의 약정 할인을 반영해 더하고 그 달 일수로 나눈다. 개통 전 날은 0원이다.
    // 요금제 변경·약정 만료·개통일로 하루 요금이 달라지는 달은 prorated가 true
    private MonthlyFee monthlyFee(Subscription subscription, List<SubscriptionHistory> planHistories,
            LocalDate month, LocalDate activationDate) {
        Map<Long, Plan> plans = new HashMap<>();
        Set<Integer> dailyFees = new HashSet<>();
        long total = 0;
        for (LocalDate day = month; day.getMonth() == month.getMonth(); day = day.plusDays(1)) {
            int fee = 0;
            if (!day.isBefore(activationDate)) {
                Instant startOfDay = startOfDay(day);
                Long planId = PlanTimeline.planAt(planHistories, startOfDay);
                Plan plan = plans.computeIfAbsent(planId, id -> planRepository.findById(id).orElseThrow());
                fee = feeAfterContract(subscription, plan.getMonthlyFee(), startOfDay);
            }
            dailyFees.add(fee);
            total += fee;
        }
        return new MonthlyFee((int) Math.round((double) total / month.lengthOfMonth()), dailyFees.size() > 1);
    }

    private record MonthlyFee(int amount, boolean prorated) {
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
}
