package com.telme.subscription.service;

import com.telme.global.common.TimeZones;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Component;

// 구독 규칙의 날짜 계산은 모두 KST 기준(서버 시계는 UTC)
@Component
public class SubscriptionCalendar {

    // 개통 처리 시작 시각(SUBSCRIBE-03: 매일 09:00~20:00)
    private static final LocalTime ACTIVATION_START = LocalTime.of(9, 0);

    private final Clock clock;

    public SubscriptionCalendar(Clock clock) {
        this.clock = clock;
    }

    public Instant now() {
        return clock.instant();
    }

    public LocalDate today(Instant now) {
        return LocalDate.ofInstant(now, TimeZones.KST);
    }

    public Instant startOfDay(Instant at) {
        return today(at).atStartOfDay(TimeZones.KST).toInstant();
    }

    public Instant startOfMonth(Instant now) {
        return today(now).withDayOfMonth(1).atStartOfDay(TimeZones.KST).toInstant();
    }

    public Instant startOfNextMonth(Instant now) {
        return today(now).withDayOfMonth(1).plusMonths(1).atStartOfDay(TimeZones.KST).toInstant();
    }

    public Instant startOfNextDay(Instant now) {
        return today(now).plusDays(1).atStartOfDay(TimeZones.KST).toInstant();
    }

    public Instant nextDayActivation(Instant now) {
        return today(now).plusDays(1).atTime(ACTIVATION_START).atZone(TimeZones.KST).toInstant();
    }

    public Instant startOfYear(Instant at) {
        return LocalDate.of(today(at).getYear(), 1, 1).atStartOfDay(TimeZones.KST).toInstant();
    }

    public Instant startOfNextYear(Instant at) {
        return LocalDate.of(today(at).getYear() + 1, 1, 1).atStartOfDay(TimeZones.KST).toInstant();
    }

    public int monthsBetween(Instant from, Instant to) {
        return (int) ChronoUnit.MONTHS.between(today(from), today(to));
    }

    public Instant plusMonths(Instant base, int months) {
        return base.atZone(TimeZones.KST).plusMonths(months).toInstant();
    }

    public boolean isSameMonth(Instant a, Instant b) {
        return YearMonth.from(today(a)).equals(YearMonth.from(today(b)));
    }
}
