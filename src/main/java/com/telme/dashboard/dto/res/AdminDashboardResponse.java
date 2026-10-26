package com.telme.dashboard.dto.res;

public record AdminDashboardResponse(
        long unansweredCount,
        long unhandledFeedbackCount,
        long failedAnswerCount,
        long todayQuestionCount,
        long yesterdayQuestionCount
) {
}
