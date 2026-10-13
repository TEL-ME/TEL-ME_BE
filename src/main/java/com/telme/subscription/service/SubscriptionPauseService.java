package com.telme.subscription.service;

import com.telme.global.common.exception.GeneralException;
import com.telme.subscription.entity.Subscription;
import com.telme.subscription.entity.SubscriptionPause;
import com.telme.subscription.exception.SubscriptionErrorCode;
import com.telme.subscription.repository.SubscriptionPauseRepository;
import com.telme.subscription.repository.SubscriptionRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 연간은 달력 연도(KST) 기준. 구독 행을 잠가 동시 요청에도 기간이 겹치거나 한도를 넘지 않는다
@Service
@RequiredArgsConstructor
@Transactional
public class SubscriptionPauseService {

    private static final long SECONDS_PER_DAY = Duration.ofDays(1).toSeconds();

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionPauseRepository pauseRepository;
    private final SubscriptionStateReader stateReader;
    private final SubscriptionCalendar calendar;

    public SubscriptionSnapshot pause(Long userId, int days) {
        Instant now = calendar.now();
        Subscription subscription = findActiveSubscriptionForUpdate(userId);
        Long subscriptionId = subscription.getSubscriptionId();

        if (days < 1 || days > PausePolicy.MAX_DAYS_PER_PAUSE) {
            throw new GeneralException(SubscriptionErrorCode.INVALID_PAUSE_DAYS);
        }
        if (stateReader.findCurrentPlan(subscriptionId, now).isEmpty()) {
            throw new GeneralException(SubscriptionErrorCode.PAUSE_NOT_ALLOWED_BEFORE_ACTIVATION);
        }
        if (pauseRepository.findActive(subscriptionId, now).isPresent()) {
            throw new GeneralException(SubscriptionErrorCode.ALREADY_PAUSED);
        }
        Instant endsAt = now.plus(Duration.ofDays(days));
        checkAnnualLimit(subscriptionId, now, endsAt);

        pauseRepository.save(SubscriptionPause.start(subscriptionId, now, endsAt));
        return stateReader.read(subscription, now);
    }

    public SubscriptionSnapshot resume(Long userId) {
        Instant now = calendar.now();
        Subscription subscription = findActiveSubscriptionForUpdate(userId);

        SubscriptionPause active = pauseRepository.findActive(subscription.getSubscriptionId(), now)
                .orElseThrow(() -> new GeneralException(SubscriptionErrorCode.PAUSE_NOT_FOUND));
        active.endAt(now);
        return stateReader.read(subscription, now);
    }

    // 연말에 걸친 정지는 각 연도 몫을 그 연도 한도에 계산
    private void checkAnnualLimit(Long subscriptionId, Instant start, Instant end) {
        Set<Instant> yearStarts = new LinkedHashSet<>();
        yearStarts.add(calendar.startOfYear(start));
        yearStarts.add(calendar.startOfYear(end.minusMillis(1)));
        for (Instant yearStart : yearStarts) {
            Instant yearEnd = calendar.startOfNextYear(yearStart);
            long used = pauseRepository.findOverlapping(subscriptionId, yearStart, yearEnd).stream()
                    .mapToLong(pause -> days(pause.getStartedAt(), pause.getEndsAt(), yearStart, yearEnd))
                    .sum();
            long requested = days(start, end, yearStart, yearEnd);
            if (used + requested > PausePolicy.MAX_DAYS_PER_YEAR) {
                throw new GeneralException(SubscriptionErrorCode.PAUSE_ANNUAL_LIMIT_EXCEEDED);
            }
        }
    }

    // 하루 미만도 하루로 계산
    private long days(Instant start, Instant end, Instant from, Instant to) {
        Instant overlapStart = start.isAfter(from) ? start : from;
        Instant overlapEnd = end.isBefore(to) ? end : to;
        long seconds = Duration.between(overlapStart, overlapEnd).getSeconds();
        return seconds <= 0 ? 0 : (seconds + SECONDS_PER_DAY - 1) / SECONDS_PER_DAY;
    }

    private Subscription findActiveSubscriptionForUpdate(Long userId) {
        return subscriptionRepository.findByUserIdAndStatusForUpdate(userId, Subscription.Status.ACTIVE)
                .orElseThrow(() -> new GeneralException(SubscriptionErrorCode.SUBSCRIPTION_NOT_FOUND));
    }
}
