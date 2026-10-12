package com.telme.subscription.repository;

import com.telme.subscription.entity.Subscription;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    Optional<Subscription> findByUserIdAndStatus(Long userId, Subscription.Status status);

    // 변경·해지가 같은 구독에 동시에 들어와도 월 1회 규칙이 깨지지 않게 구독 행을 잠근다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select subscription
            from Subscription subscription
            where subscription.userId = :userId
              and subscription.status = :status
            """)
    Optional<Subscription> findByUserIdAndStatusForUpdate(
            @Param("userId") Long userId, @Param("status") Subscription.Status status);
}
