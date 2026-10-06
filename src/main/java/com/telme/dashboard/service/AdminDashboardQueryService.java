package com.telme.dashboard.service;

import com.telme.chat.entity.ChatMessage;
import com.telme.chat.repository.AdminChatStatsRepository;
import com.telme.chat.repository.AdminUnansweredRepository;
import com.telme.chat.repository.QuestionCounts;
import com.telme.dashboard.dto.req.AdminDashboardSearchRequest;
import com.telme.dashboard.dto.res.AdminDashboardResponse;
import com.telme.dashboard.exception.DashboardErrorCode;
import com.telme.feedback.entity.MessageFeedback;
import com.telme.feedback.repository.AdminFeedbackRepository;
import com.telme.global.common.exception.GeneralException;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
// 숫자마다 다른 시점을 보면 실패한 답변 수가 답 못 한 질문 수보다 커질 수 있다
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class AdminDashboardQueryService {

    private static final List<ChatMessage.Status> FAILED_STATUSES =
            List.of(ChatMessage.Status.FAILED, ChatMessage.Status.TIMEOUT);

    private final AdminUnansweredRepository unansweredRepository;
    private final AdminChatStatsRepository chatStatsRepository;
    private final AdminFeedbackRepository feedbackRepository;
    private final Clock clock;

    public AdminDashboardResponse getSummary(AdminDashboardSearchRequest request) {
        if (request.periodReversed()) {
            throw new GeneralException(DashboardErrorCode.INVALID_PERIOD);
        }
        DashboardDays days = DashboardDays.of(clock);
        QuestionCounts questions = chatStatsRepository.countQuestions(
                days.yesterdayStart(), days.todayStart(), days.tomorrowStart());
        return new AdminDashboardResponse(
                unansweredRepository.countUnanswered(request.fromOrMin(), request.toOrMax()),
                feedbackRepository.countUnhandledDislikes(MessageFeedback.Rating.DISLIKE),
                unansweredRepository.countUnansweredByStatuses(
                        FAILED_STATUSES, request.fromOrMin(), request.toOrMax()),
                questions.today(),
                questions.yesterday());
    }
}
