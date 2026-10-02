package com.telme.dashboard.service;

import com.telme.global.common.TimeZones;
import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;

// 서버 시계가 UTC라 날짜로 그냥 끊으면 한국 시간 오전 9시에 "오늘"이 바뀐다.
// 오늘은 [todayStart, tomorrowStart), 어제는 [yesterdayStart, todayStart)
public record DashboardDays(Instant yesterdayStart, Instant todayStart, Instant tomorrowStart) {

    public static DashboardDays of(Clock clock) {
        ZonedDateTime todayStart = clock.instant().atZone(TimeZones.KST)
                .toLocalDate().atStartOfDay(TimeZones.KST);
        return new DashboardDays(
                todayStart.minusDays(1).toInstant(),
                todayStart.toInstant(),
                todayStart.plusDays(1).toInstant());
    }
}
