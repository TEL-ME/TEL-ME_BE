package com.telme.dashboard.service;

import com.telme.chat.entity.ChatMessage;
import com.telme.chat.repository.AdminChatStatsRepository;
import com.telme.chat.repository.AdminUnansweredRepository;
import com.telme.dashboard.dto.res.AdminDashboardResponse;
import com.telme.feedback.entity.MessageFeedback;
import com.telme.feedback.repository.AdminFeedbackRepository;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardQueryService {

    // 답 못 한 질문 중 FAQ로 해결되지 않는 것만 따로 센다
    private static final List<ChatMessage.Status> FAILED_STATUSES =
            List.of(ChatMessage.Status.FAILED, ChatMessage.Status.TIMEOUT);

    private final AdminUnansweredRepository unansweredRepository;
    private final AdminChatStatsRepository chatStatsRepository;
    private final AdminFeedbackRepository feedbackRepository;
    private final Clock clock;

    public AdminDashboardResponse getSummary() {
        DashboardDays days = DashboardDays.of(clock);
        return new AdminDashboardResponse(
                unansweredRepository.countUnanswered(),
                feedbackRepository.countUnhandledDislikes(MessageFeedback.Rating.DISLIKE),
                unansweredRepository.countUnansweredByStatuses(FAILED_STATUSES),
                chatStatsRepository.countQuestions(days.todayStart(), days.tomorrowStart()),
                chatStatsRepository.countQuestions(days.yesterdayStart(), days.todayStart()));
    }
}
