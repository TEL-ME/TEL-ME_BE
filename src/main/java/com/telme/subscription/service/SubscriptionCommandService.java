package com.telme.subscription.service;

import com.telme.global.common.exception.GeneralException;
import com.telme.member.entity.User;
import com.telme.member.exception.MemberErrorCode;
import com.telme.member.repository.UserRepository;
import com.telme.plan.entity.Plan;
import com.telme.plan.exception.PlanErrorCode;
import com.telme.plan.repository.PlanRepository;
import com.telme.subscription.entity.Subscription;
import com.telme.subscription.entity.SubscriptionHistory;
import com.telme.subscription.exception.SubscriptionErrorCode;
import com.telme.subscription.repository.SubscriptionHistoryRepository;
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
    private final SubscriptionCalendar calendar;
    private final SubscriptionEligibilityChecker eligibilityChecker;
    private final SubscriptionStateReader stateReader;

    // 정책에 가입 적용 시점 규정이 없어 즉시 적용
    public SubscriptionSnapshot subscribe(Long userId, Long planId, LocalDate birthDate) {
        Instant now = calendar.now();
        User user = findUser(userId);
        Plan plan = findSellingPlan(planId);
        if (subscriptionRepository.findByUserIdAndStatus(userId, Subscription.Status.ACTIVE).isPresent()) {
            throw new GeneralException(SubscriptionErrorCode.ALREADY_SUBSCRIBED);
        }
        eligibilityChecker.verifyAndRemember(user, plan, birthDate, calendar.today(now));

        Subscription subscription;
        try {
            subscription = subscriptionRepository.saveAndFlush(Subscription.subscribe(userId, now));
        } catch (DataIntegrityViolationException e) {
            // 동시 가입 요청은 활성 구독 유니크 인덱스에서 차단
            throw new GeneralException(SubscriptionErrorCode.ALREADY_SUBSCRIBED);
        }
        historyRepository.save(SubscriptionHistory.subscribed(subscription.getSubscriptionId(), plan.getPlanId(), now));
        return stateReader.read(subscription, now);
    }

    // BILLING-01 규칙 적용. 구독 행을 잠가 동시 요청에도 월 1회 유지
    public SubscriptionSnapshot changePlan(Long userId, Long planId, LocalDate birthDate) {
        Instant now = calendar.now();
        Subscription subscription = findActiveSubscriptionForUpdate(userId);
        Plan plan = findSellingPlan(planId);
        Plan currentPlan = stateReader.currentPlan(subscription.getSubscriptionId(), now);

        if (currentPlan.getPlanId().equals(plan.getPlanId())) {
            throw new GeneralException(SubscriptionErrorCode.SAME_PLAN);
        }
        if (calendar.isSameMonth(subscription.getSubscribedAt(), now)) {
            throw new GeneralException(SubscriptionErrorCode.CHANGE_NOT_ALLOWED_IN_SUBSCRIBED_MONTH);
        }
        if (historyRepository.existsChangeRequestedBetween(
                subscription.getSubscriptionId(), calendar.startOfMonth(now), calendar.startOfNextMonth(now))) {
            throw new GeneralException(SubscriptionErrorCode.MONTHLY_CHANGE_LIMIT_EXCEEDED);
        }
        eligibilityChecker.verifyAndRemember(findUser(userId), plan, birthDate, calendar.today(now));

        historyRepository.save(SubscriptionHistory.changed(
                subscription.getSubscriptionId(), currentPlan.getPlanId(), plan.getPlanId(),
                calendar.startOfNextDay(now), now));
        return stateReader.read(subscription, now);
    }

    // 즉시 해지(TERMINATE-01). 예약된 변경은 구독 종료와 함께 소멸
    public void cancel(Long userId) {
        Instant now = calendar.now();
        Subscription subscription = findActiveSubscriptionForUpdate(userId);
        Plan currentPlan = stateReader.currentPlan(subscription.getSubscriptionId(), now);

        subscription.cancel(now);
        historyRepository.save(SubscriptionHistory.canceled(subscription.getSubscriptionId(), currentPlan.getPlanId(), now));
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
