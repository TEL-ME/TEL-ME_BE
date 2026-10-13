package com.telme.usage.service;

import java.time.Instant;

// 미가입과 개통 전은 오류가 아니라 상태. usage는 ACTIVE일 때만 있고 activatesAt은 PENDING_ACTIVATION일 때만 있다
public record UsageOverview(Status status, Instant activatesAt, ThisMonthUsage usage) {

    public enum Status { NOT_SUBSCRIBED, PENDING_ACTIVATION, ACTIVE }

    public static UsageOverview notSubscribed() {
        return new UsageOverview(Status.NOT_SUBSCRIBED, null, null);
    }

    public static UsageOverview pendingActivation(Instant activatesAt) {
        return new UsageOverview(Status.PENDING_ACTIVATION, activatesAt, null);
    }

    public static UsageOverview active(ThisMonthUsage usage) {
        return new UsageOverview(Status.ACTIVE, null, usage);
    }
}
