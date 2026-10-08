package com.telme.dashboard.dto.res;

import java.time.LocalDate;
import java.util.List;

public record AdminDashboardDailyResponse(List<Day> days) {

    public record Day(LocalDate date, long questionCount, long errorCount) {
    }
}
