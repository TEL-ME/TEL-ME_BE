package com.telme.subscription.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 현재 요금제는 저장하지 않고 SubscriptionHistory에서 계산
@Entity
@Table(name = "user_subscriptions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Subscription {

    public enum Status { ACTIVE, CANCELED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "subscription_id")
    private Long subscriptionId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status;

    @Column(name = "subscribed_at", nullable = false)
    private Instant subscribedAt;

    @Column(name = "canceled_at")
    private Instant canceledAt;

    // 약정이 없으면 둘 다 null
    @Column(name = "contract_months")
    private Integer contractMonths;

    @Column(name = "contract_discount_percent")
    private Integer contractDiscountPercent;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, updatable = false, insertable = false)
    private Instant updatedAt;

    public static Subscription subscribe(Long userId, Instant now) {
        return subscribe(userId, now, null, null);
    }

    public static Subscription subscribe(
            Long userId, Instant now, Integer contractMonths, Integer contractDiscountPercent) {
        return Subscription.builder()
                .userId(userId)
                .status(Status.ACTIVE)
                .subscribedAt(now)
                .contractMonths(contractMonths)
                .contractDiscountPercent(contractDiscountPercent)
                .build();
    }

    public boolean hasContract() {
        return contractMonths != null;
    }

    public void cancel(Instant now) {
        this.status = Status.CANCELED;
        this.canceledAt = now;
    }
}
