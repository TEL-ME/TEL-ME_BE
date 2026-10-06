package com.telme.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.telme.dashboard.service.DashboardDays;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DashboardDaysTest {

    @Test
    @DisplayName("한국 시간 자정으로 하루를 끊는다")
    void 한국_시간_자정으로_끊는다() {
        // 2026-10-02T06:00Z = 한국 시간 10월 2일 오후 3시
        DashboardDays days = DashboardDays.of(fixed("2026-10-02T06:00:00Z"));

        assertThat(days.todayStart()).isEqualTo(Instant.parse("2026-10-01T15:00:00Z"));
        assertThat(days.tomorrowStart()).isEqualTo(Instant.parse("2026-10-02T15:00:00Z"));
        assertThat(days.yesterdayStart()).isEqualTo(Instant.parse("2026-09-30T15:00:00Z"));
    }

    @Test
    @DisplayName("한국 시간으로 날이 바뀌는 순간 오늘이 넘어간다")
    void 자정_직전과_직후가_다른_날이다() {
        // UTC 15:00 = 한국 시간 다음 날 0시
        DashboardDays before = DashboardDays.of(fixed("2026-10-02T14:59:59Z"));
        DashboardDays after = DashboardDays.of(fixed("2026-10-02T15:00:00Z"));

        assertThat(before.todayStart()).isEqualTo(Instant.parse("2026-10-01T15:00:00Z"));
        assertThat(after.todayStart()).isEqualTo(Instant.parse("2026-10-02T15:00:00Z"));
    }

    @Test
    @DisplayName("UTC 자정을 넘어도 한국 시간 날짜는 그대로다")
    void UTC_자정은_경계가_아니다() {
        // 한국 시간으로는 둘 다 10월 2일
        DashboardDays before = DashboardDays.of(fixed("2026-10-01T23:59:59Z"));
        DashboardDays after = DashboardDays.of(fixed("2026-10-02T00:00:00Z"));

        assertThat(before.todayStart()).isEqualTo(after.todayStart());
    }

    private Clock fixed(String instant) {
        return Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
    }
}
