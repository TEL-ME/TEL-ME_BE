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

// createdAt은 월 1회 판단용 신청 시각이라 DB 기본값 대신 Clock 값 사용
@Entity
@Table(name = "subscription_histories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class SubscriptionHistory {

    public enum Type { SUBSCRIBE, CHANGE, CANCEL }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    private Long historyId;

    @Column(name = "subscription_id", nullable = false)
    private Long subscriptionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private Type type;

    @Column(name = "from_plan_id")
    private Long fromPlanId;

    @Column(name = "to_plan_id")
    private Long toPlanId;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static SubscriptionHistory subscribed(Long subscriptionId, Long toPlanId, Instant now) {
        return SubscriptionHistory.builder()
                .subscriptionId(subscriptionId)
                .type(Type.SUBSCRIBE)
                .toPlanId(toPlanId)
                .effectiveFrom(now)
                .createdAt(now)
                .build();
    }

    public static SubscriptionHistory changed(
            Long subscriptionId, Long fromPlanId, Long toPlanId, Instant effectiveFrom, Instant now) {
        return SubscriptionHistory.builder()
                .subscriptionId(subscriptionId)
                .type(Type.CHANGE)
                .fromPlanId(fromPlanId)
                .toPlanId(toPlanId)
                .effectiveFrom(effectiveFrom)
                .createdAt(now)
                .build();
    }

    public static SubscriptionHistory canceled(Long subscriptionId, Long fromPlanId, Instant now) {
        return SubscriptionHistory.builder()
                .subscriptionId(subscriptionId)
                .type(Type.CANCEL)
                .fromPlanId(fromPlanId)
                .effectiveFrom(now)
                .createdAt(now)
                .build();
    }
}
