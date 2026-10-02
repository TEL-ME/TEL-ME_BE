package com.telme.dashboard.dto.res;

// 증감은 두 숫자를 주고 화면이 계산한다. 어제가 0일 때 몇 %인지를 서버가 정하지 않기 위해서다
public record AdminDashboardResponse(
        long unansweredCount,
        long unhandledFeedbackCount,
        long failedAnswerCount,
        long todayQuestionCount,
        long yesterdayQuestionCount
) {
}
