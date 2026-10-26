package com.telme.subscription.repository;

import com.telme.subscription.entity.SubscriptionHistory;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionHistoryRepository extends JpaRepository<SubscriptionHistory, Long> {

    List<SubscriptionHistory.Type> PLAN_TYPES =
            List.of(SubscriptionHistory.Type.SUBSCRIBE, SubscriptionHistory.Type.CHANGE);

    // 현재 요금제 = 적용 시작이 지난 가장 최근 가입·변경
    Optional<SubscriptionHistory> findFirstBySubscriptionIdAndTypeInAndEffectiveFromLessThanEqualOrderByEffectiveFromDescHistoryIdDesc(
            Long subscriptionId, List<SubscriptionHistory.Type> types, Instant now);

    default Optional<SubscriptionHistory> findCurrent(Long subscriptionId, Instant now) {
        return findFirstBySubscriptionIdAndTypeInAndEffectiveFromLessThanEqualOrderByEffectiveFromDescHistoryIdDesc(
                subscriptionId, PLAN_TYPES, now);
    }

    Optional<SubscriptionHistory> findFirstBySubscriptionIdAndTypeAndEffectiveFromAfterOrderByEffectiveFromAsc(
            Long subscriptionId, SubscriptionHistory.Type type, Instant now);

    default Optional<SubscriptionHistory> findPendingChange(Long subscriptionId, Instant now) {
        return findFirstBySubscriptionIdAndTypeAndEffectiveFromAfterOrderByEffectiveFromAsc(
                subscriptionId, SubscriptionHistory.Type.CHANGE, now);
    }

    default Optional<SubscriptionHistory> findPendingActivation(Long subscriptionId, Instant now) {
        return findFirstBySubscriptionIdAndTypeAndEffectiveFromAfterOrderByEffectiveFromAsc(
                subscriptionId, SubscriptionHistory.Type.SUBSCRIBE, now);
    }

    // 위약금의 월별 요금제 계산용
    List<SubscriptionHistory> findBySubscriptionIdOrderByEffectiveFromAscHistoryIdAsc(Long subscriptionId);

    // 신청 시각 기준(예약된 변경 포함)
    boolean existsBySubscriptionIdAndTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            Long subscriptionId, SubscriptionHistory.Type type, Instant from, Instant to);

    default boolean existsChangeRequestedBetween(Long subscriptionId, Instant from, Instant to) {
        return existsBySubscriptionIdAndTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                subscriptionId, SubscriptionHistory.Type.CHANGE, from, to);
    }
}
