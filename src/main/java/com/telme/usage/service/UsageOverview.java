package com.telme.usage.service;

import java.time.Instant;
import java.time.LocalDate;

// 미가입과 개통 전은 오류가 아니라 상태. usage·availableFrom·availableTo(조회 가능한 달, 각 달의 1일)는 ACTIVE일 때만, activatesAt은 PENDING_ACTIVATION일 때만 있다
public record UsageOverview(
        Status status, Instant activatesAt, LocalDate availableFrom, LocalDate availableTo, MonthUsage usage) {

    public enum Status { NOT_SUBSCRIBED, PENDING_ACTIVATION, ACTIVE }

    public static UsageOverview notSubscribed() {
        return new UsageOverview(Status.NOT_SUBSCRIBED, null, null, null, null);
    }

    public static UsageOverview pendingActivation(Instant activatesAt) {
        return new UsageOverview(Status.PENDING_ACTIVATION, activatesAt, null, null, null);
    }

    public static UsageOverview active(LocalDate availableFrom, LocalDate availableTo, MonthUsage usage) {
        return new UsageOverview(Status.ACTIVE, null, availableFrom, availableTo, usage);
    }
}
