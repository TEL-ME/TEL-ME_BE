package com.telme.subscription.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

// endsAt은 신청 때 정한 종료 예정이고 해제하면 해제 시각으로 당긴다. 기간이 지난 정지는 조회 시점에 끝난 것으로 계산
@Entity
@Table(name = "subscription_pauses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class SubscriptionPause {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pause_id")
    private Long pauseId;

    @Column(name = "subscription_id", nullable = false)
    private Long subscriptionId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static SubscriptionPause start(Long subscriptionId, Instant now, Instant endsAt) {
        return SubscriptionPause.builder()
                .subscriptionId(subscriptionId)
                .startedAt(now)
                .endsAt(endsAt)
                .createdAt(now)
                .build();
    }

    public boolean isActiveAt(Instant now) {
        return !now.isBefore(startedAt) && now.isBefore(endsAt);
    }

    public void endAt(Instant now) {
        if (now.isBefore(endsAt)) {
            this.endsAt = now;
        }
    }
}
