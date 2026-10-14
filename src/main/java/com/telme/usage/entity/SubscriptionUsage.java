package com.telme.usage.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// usageMonth는 해당 월의 1일(KST)이고, 이번 달 값도 월 전체 기준이다
@Entity
@Table(name = "subscription_usages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class SubscriptionUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "usage_id")
    private Long usageId;

    @Column(name = "subscription_id", nullable = false)
    private Long subscriptionId;

    @Column(name = "usage_month", nullable = false)
    private LocalDate usageMonth;

    @Column(name = "used_data_mb", nullable = false)
    private long usedDataMb;

    @Column(name = "used_voice_minutes", nullable = false)
    private int usedVoiceMinutes;

    @Column(name = "used_sms_count", nullable = false)
    private int usedSmsCount;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;
}
