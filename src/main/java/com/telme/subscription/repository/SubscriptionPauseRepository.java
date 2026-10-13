package com.telme.subscription.repository;

import com.telme.subscription.entity.SubscriptionPause;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionPauseRepository extends JpaRepository<SubscriptionPause, Long> {

    // 시작 시각 포함, 종료 시각 미포함
    Optional<SubscriptionPause> findFirstBySubscriptionIdAndStartedAtLessThanEqualAndEndsAtGreaterThanOrderByStartedAtDesc(
            Long subscriptionId, Instant startedAtBefore, Instant endsAfter);

    default Optional<SubscriptionPause> findActive(Long subscriptionId, Instant now) {
        return findFirstBySubscriptionIdAndStartedAtLessThanEqualAndEndsAtGreaterThanOrderByStartedAtDesc(
                subscriptionId, now, now);
    }

    // [from, to) 구간과 겹치는 정지. 연간 사용일 계산용
    List<SubscriptionPause> findBySubscriptionIdAndStartedAtLessThanAndEndsAtGreaterThan(
            Long subscriptionId, Instant to, Instant from);

    default List<SubscriptionPause> findOverlapping(Long subscriptionId, Instant from, Instant to) {
        return findBySubscriptionIdAndStartedAtLessThanAndEndsAtGreaterThan(subscriptionId, to, from);
    }
}
