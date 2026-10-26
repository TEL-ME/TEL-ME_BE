package com.telme.dashboard.service;

import com.telme.chat.entity.ChatMessage;
import com.telme.chat.repository.AdminChatStatsRepository;
import com.telme.chat.repository.AdminUnansweredRepository;
import com.telme.chat.repository.QuestionCounts;
import com.telme.dashboard.dto.req.AdminDashboardSearchRequest;
import com.telme.dashboard.dto.res.AdminDashboardDailyResponse;
import com.telme.dashboard.dto.res.AdminDashboardResponse;
import com.telme.dashboard.exception.DashboardErrorCode;
import com.telme.feedback.entity.MessageFeedback;
import com.telme.feedback.repository.AdminFeedbackRepository;
import com.telme.global.common.DailyCount;
import com.telme.global.common.TimeZones;
import com.telme.global.common.exception.GeneralException;
import com.telme.llm.repository.LlmGenerationRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
    
    private static final int DAILY_DAYS = 7;

    private final AdminUnansweredRepository unansweredRepository;
    private final AdminChatStatsRepository chatStatsRepository;
    private final AdminFeedbackRepository feedbackRepository;
    private final LlmGenerationRepository llmGenerationRepository;
    private final Clock clock;

    public AdminDashboardResponse getSummary(AdminDashboardSearchRequest request) {
        if (request.periodReversed()) {
            throw new GeneralException(DashboardErrorCode.INVALID_PERIOD);
        }
        DashboardDays days = DashboardDays.of(clock);
        QuestionCounts questions = chatStatsRepository.countQuestions(
                days.yesterdayStart(), days.todayStart(), days.tomorrowStart());
                
        long unanswered;
        long failed;
        if (request.from() == null && request.to() == null) {
            unanswered = unansweredRepository.countUnansweredAll();
            failed = unansweredRepository.countUnansweredByStatusesAll(FAILED_STATUSES);
        } else {
            unanswered = unansweredRepository.countUnanswered(request.fromOrMin(), request.toOrMax());
            failed = unansweredRepository.countUnansweredByStatuses(
                    FAILED_STATUSES, request.fromOrMin(), request.toOrMax());
        }
        
        return new AdminDashboardResponse(
                unanswered,
                feedbackRepository.countUnhandledDislikes(MessageFeedback.Rating.DISLIKE),
                failed,
                questions.today(),
                questions.yesterday());
    }
    
    // 오늘을 포함한 최근 7일. 오늘은 아직 끝나지 않은 날이라 지금까지의 숫자다
    public AdminDashboardDailyResponse getDaily() {
        LocalDate today = clock.instant().atZone(TimeZones.KST).toLocalDate();
        LocalDate firstDay = today.minusDays(DAILY_DAYS - 1);
        Instant from = firstDay.atStartOfDay(TimeZones.KST).toInstant();
        Instant to = today.plusDays(1).atStartOfDay(TimeZones.KST).toInstant();

        Map<LocalDate, Long> questions = byDay(chatStatsRepository.countQuestionsByDay(from, to));
        Map<LocalDate, Long> errors = byDay(llmGenerationRepository.countErrorsByDay(from, to));
        return new AdminDashboardDailyResponse(firstDay.datesUntil(today.plusDays(1))
                .map(day -> new AdminDashboardDailyResponse.Day(
                        day, questions.getOrDefault(day, 0L), errors.getOrDefault(day, 0L)))
                .toList());
    }
    
    private Map<LocalDate, Long> byDay(List<DailyCount> counts) {
        return counts.stream().collect(Collectors.toMap(DailyCount::getDay, DailyCount::getCount));
    }
}
