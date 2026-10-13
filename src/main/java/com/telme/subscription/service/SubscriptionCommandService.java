package com.telme.subscription.service;

import com.telme.global.common.exception.GeneralException;
import com.telme.member.entity.User;
import com.telme.member.exception.MemberErrorCode;
import com.telme.member.repository.UserRepository;
import com.telme.plan.entity.Plan;
import com.telme.plan.exception.PlanErrorCode;
import com.telme.plan.repository.PlanRepository;
import com.telme.subscription.config.SubscriptionProperties;
import com.telme.subscription.entity.Subscription;
import com.telme.subscription.entity.SubscriptionHistory;
import com.telme.subscription.exception.SubscriptionErrorCode;
import com.telme.subscription.repository.SubscriptionHistoryRepository;
import com.telme.subscription.repository.SubscriptionPauseRepository;
import com.telme.subscription.repository.SubscriptionRepository;
import java.time.Instant;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class SubscriptionCommandService {

    private final UserRepository userRepository;
    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionHistoryRepository historyRepository;
    private final SubscriptionPauseRepository pauseRepository;
    private final SubscriptionCalendar calendar;
    private final SubscriptionEligibilityChecker eligibilityChecker;
    private final SubscriptionStateReader stateReader;
    private final SubscriptionProperties properties;
    private final ContractPenaltyCalculator penaltyCalculator;

    // 온라인 신청은 다음 날 09:00 개통(SUBSCRIBE-03). 시연 모드는 즉시 개통
    public SubscriptionSnapshot subscribe(Long userId, Long planId, LocalDate birthDate) {
        return subscribe(userId, planId, birthDate, false);
    }

    public SubscriptionSnapshot subscribe(Long userId, Long planId, LocalDate birthDate, boolean contract) {
        Instant now = calendar.now();
        User user = findUser(userId);
        Plan plan = findSellingPlan(planId);
        if (subscriptionRepository.findByUserIdAndStatus(userId, Subscription.Status.ACTIVE).isPresent()) {
            throw new GeneralException(SubscriptionErrorCode.ALREADY_SUBSCRIBED);
        }
        eligibilityChecker.verifyAndRemember(user, plan, birthDate, calendar.today(now));

        Subscription subscription;
        try {
            subscription = subscriptionRepository.saveAndFlush(contract
                    ? Subscription.subscribe(userId, now, ContractPolicy.CONTRACT_MONTHS, ContractPolicy.DISCOUNT_PERCENT)
                    : Subscription.subscribe(userId, now));
        } catch (DataIntegrityViolationException e) {
            // 동시 가입 요청은 활성 구독 유니크 인덱스에서 차단
            throw new GeneralException(SubscriptionErrorCode.ALREADY_SUBSCRIBED);
        }
        Instant activatesAt = properties.demoMode() ? now : calendar.nextDayActivation(now);
        historyRepository.save(SubscriptionHistory.subscribed(
                subscription.getSubscriptionId(), plan.getPlanId(), activatesAt, now));
        return stateReader.read(subscription, now);
    }

    // BILLING-01 규칙 적용. 구독 행을 잠가 동시 요청에도 월 1회 유지
    public SubscriptionSnapshot changePlan(Long userId, Long planId, LocalDate birthDate) {
        Instant now = calendar.now();
        Subscription subscription = findActiveSubscriptionForUpdate(userId);
        Plan plan = findSellingPlan(planId);

        // 시연 모드는 가입 직후 변경도 보여주려고 가입 당월·월 1회 제한 생략
        if (!properties.demoMode() && calendar.isSameMonth(subscription.getSubscribedAt(), now)) {
            throw new GeneralException(SubscriptionErrorCode.CHANGE_NOT_ALLOWED_IN_SUBSCRIBED_MONTH);
        }
        Plan currentPlan = stateReader.findCurrentPlan(subscription.getSubscriptionId(), now)
                .orElseThrow(() -> new GeneralException(SubscriptionErrorCode.CHANGE_NOT_ALLOWED_BEFORE_ACTIVATION));
        if (pauseRepository.findActive(subscription.getSubscriptionId(), now).isPresent()) {
            throw new GeneralException(SubscriptionErrorCode.CHANGE_NOT_ALLOWED_WHILE_PAUSED);
        }
        if (currentPlan.getPlanId().equals(plan.getPlanId())) {
            throw new GeneralException(SubscriptionErrorCode.SAME_PLAN);
        }
        if (!properties.demoMode() && historyRepository.existsChangeRequestedBetween(
                subscription.getSubscriptionId(), calendar.startOfMonth(now), calendar.startOfNextMonth(now))) {
            throw new GeneralException(SubscriptionErrorCode.MONTHLY_CHANGE_LIMIT_EXCEEDED);
        }
        eligibilityChecker.verifyAndRemember(findUser(userId), plan, birthDate, calendar.today(now));

        Instant appliesAt = properties.demoMode() ? now : calendar.startOfNextDay(now);
        historyRepository.save(SubscriptionHistory.changed(
                subscription.getSubscriptionId(), currentPlan.getPlanId(), plan.getPlanId(), appliesAt, now));
        return stateReader.read(subscription, now);
    }

    // 즉시 해지(TERMINATE-01). 개통 전이면 신청한 요금제가 해지 당시 요금제
    public ContractPenalty cancel(Long userId) {
        Instant now = calendar.now();
        Subscription subscription = findActiveSubscriptionForUpdate(userId);
        SubscriptionSnapshot state = stateReader.read(subscription, now);
        Plan plan = state.currentPlan() != null ? state.currentPlan() : state.pendingActivation().plan();

        ContractPenalty penalty = penaltyCalculator.calculate(subscription, now);

        pauseRepository.findActive(subscription.getSubscriptionId(), now).ifPresent(pause -> pause.endAt(now));
        subscription.cancel(now);
        historyRepository.save(SubscriptionHistory.canceled(
                subscription.getSubscriptionId(), plan.getPlanId(), now, penalty.penalty()));
        return penalty;
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(MemberErrorCode.UNAUTHENTICATED));
    }

    private Plan findSellingPlan(Long planId) {
        return planRepository.findByPlanIdAndStatus(planId, Plan.Status.ACTIVE)
                .orElseThrow(() -> new GeneralException(PlanErrorCode.PLAN_NOT_FOUND));
    }

    private Subscription findActiveSubscriptionForUpdate(Long userId) {
        return subscriptionRepository.findByUserIdAndStatusForUpdate(userId, Subscription.Status.ACTIVE)
                .orElseThrow(() -> new GeneralException(SubscriptionErrorCode.SUBSCRIPTION_NOT_FOUND));
    }
}
