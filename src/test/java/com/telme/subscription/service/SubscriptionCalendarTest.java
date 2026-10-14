package com.telme.subscription.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.telme.global.common.TimeZones;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class SubscriptionCalendarTest {

    SubscriptionCalendar calendar = new SubscriptionCalendar(Clock.systemUTC());

    @Test
    void 윤일_가입자의_1년과_2년은_평년_2월_28일에_채워진다() {
        Instant joined = kst("2028-02-29T12:00");

        assertThat(calendar.monthsBetween(joined, kst("2029-02-27T12:00"))).isEqualTo(11);
        assertThat(calendar.monthsBetween(joined, kst("2029-02-28T00:00"))).isEqualTo(12);
        assertThat(calendar.monthsBetween(joined, kst("2030-02-27T23:59"))).isEqualTo(23);
        assertThat(calendar.monthsBetween(joined, kst("2030-02-28T00:00"))).isEqualTo(24);
        assertThat(calendar.monthsBetween(joined, kst("2030-03-01T00:00"))).isEqualTo(24);
    }

    @Test
    void 말일이_짧은_달에는_그달_말일에_한_달이_채워진다() {
        Instant joined = kst("2026-01-31T12:00");

        assertThat(calendar.monthsBetween(joined, kst("2026-02-27T12:00"))).isZero();
        assertThat(calendar.monthsBetween(joined, kst("2026-02-28T00:00"))).isEqualTo(1);
        assertThat(calendar.monthsBetween(joined, kst("2026-03-30T12:00"))).isEqualTo(1);
        assertThat(calendar.monthsBetween(joined, kst("2026-03-31T00:00"))).isEqualTo(2);
    }

    @Test
    void 개월_수는_plusMonths로_만든_날에_정확히_올라간다() {
        for (String start : new String[] {"2028-02-29T12:00", "2026-01-31T12:00", "2026-01-05T12:00", "2026-08-31T23:30"}) {
            Instant joined = kst(start);
            for (int months = 1; months <= 36; months++) {
                Instant boundary = calendar.startOfDay(calendar.plusMonths(joined, months));
                assertThat(calendar.monthsBetween(joined, boundary)).as("%s +%d개월", start, months).isEqualTo(months);
                assertThat(calendar.monthsBetween(joined, boundary.minusSeconds(1)))
                        .as("%s +%d개월 직전", start, months).isEqualTo(months - 1);
            }
        }
    }

    @Test
    void 가입일_이전이면_0이거나_음수이다() {
        Instant joined = kst("2026-03-15T12:00");

        assertThat(calendar.monthsBetween(joined, kst("2026-03-14T12:00"))).isZero();
        assertThat(calendar.monthsBetween(joined, kst("2026-01-14T12:00"))).isEqualTo(-2);
    }

    private Instant kst(String dateTime) {
        return LocalDateTime.parse(dateTime).atZone(TimeZones.KST).toInstant();
    }
}
