package com.telme.dashboard.service;

import com.telme.chat.entity.ChatMessage;
import com.telme.chat.repository.AdminChatStatsRepository;
import com.telme.chat.repository.AdminUnansweredRepository;
import com.telme.dashboard.dto.req.AdminDashboardSearchRequest;
import com.telme.dashboard.dto.res.AdminDashboardResponse;
import com.telme.feedback.entity.MessageFeedback;
import com.telme.dashboard.exception.DashboardErrorCode;
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
// 다섯 번의 count가 저마다 다른 시점을 보면 실패한 답변 수가 답 못 한 질문 수보다 커질 수 있다
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class AdminDashboardQueryService {

    // 답 못 한 질문 중 FAQ로 해결되지 않는 것만 따로 센다
    private static final List<ChatMessage.Status> FAILED_STATUSES =
            List.of(ChatMessage.Status.FAILED, ChatMessage.Status.TIMEOUT);

    private final AdminUnansweredRepository unansweredRepository;
    private final AdminChatStatsRepository chatStatsRepository;
    private final AdminFeedbackRepository feedbackRepository;
    private final Clock clock;

    public AdminDashboardResponse getSummary(AdminDashboardSearchRequest request) {
        // 시작이 끝보다 뒤면 결과가 늘 0이라 조건을 잘못 넣은 것을 알아채기 어렵다
        if (request.periodReversed()) {
            throw new GeneralException(DashboardErrorCode.INVALID_PERIOD);
        }
        DashboardDays days = DashboardDays.of(clock);
        return new AdminDashboardResponse(
                unansweredRepository.countUnanswered(request.fromOrMin(), request.toOrMax()),
                feedbackRepository.countUnhandledDislikes(MessageFeedback.Rating.DISLIKE),
                unansweredRepository.countUnansweredByStatuses(
                        FAILED_STATUSES, request.fromOrMin(), request.toOrMax()),
                chatStatsRepository.countQuestions(days.todayStart(), days.tomorrowStart()),
                chatStatsRepository.countQuestions(days.yesterdayStart(), days.todayStart()));
    }
}
