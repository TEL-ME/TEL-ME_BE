package com.telme.dashboard.dto.res;

// 증감 대신 두 숫자를 준다. 어제가 0일 때 어떻게 보여줄지는 화면이 정한다
public record AdminDashboardResponse(
        long unansweredCount,
        long unhandledFeedbackCount,
        long failedAnswerCount,
        long todayQuestionCount,
        long yesterdayQuestionCount
) {
}
