package com.telme.usage.service;

import java.time.LocalDate;

// daysUntilBilling은 정산일(말일)까지 남은 일수. 사용량은 개통 이후 지난 일수만큼만 반영한 이번 달 현재 값
public record ThisMonthUsage(
        LocalDate month,
        int daysUntilBilling,
        boolean paused,
        ResourceUsage data,
        ResourceUsage voice,
        ResourceUsage sms,
        ExpectedFee expectedFee
) {
}
