package com.telme.usage.service;

import java.time.LocalDate;

// 이번 달은 개통 이후 지난 일수만큼만 반영한 현재 값이고, 지난 달은 그달의 사용량(개통한 달은 개통 이후 일수만큼)이다.
// daysUntilBilling(정산일=말일까지 남은 일수)·paused·expectedFee는 이번 달에만 있고 지난 달은 null
public record MonthUsage(
        LocalDate month,
        Integer daysUntilBilling,
        Boolean paused,
        ResourceUsage data,
        ResourceUsage voice,
        ResourceUsage sms,
        ExpectedFee expectedFee
) {
}
