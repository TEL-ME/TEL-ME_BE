package com.telme.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.telme.dashboard.dto.req.AdminDashboardSearchRequest;
import com.telme.dashboard.dto.res.AdminDashboardDailyResponse;
import com.telme.dashboard.dto.res.AdminDashboardResponse;
import com.telme.dashboard.exception.DashboardErrorCode;
import com.telme.dashboard.service.AdminDashboardQueryService;
import com.telme.global.common.exception.GeneralException;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

// 시드와 로컬 데이터가 섞여 절대 건수는 환경마다 다르다. 넣기 전후의 차이만 본다.
// 각 테스트는 트랜잭션 롤백으로 데이터에 영향 없음
@SpringBootTest
@Transactional
class AdminDashboardQueryServiceTest {

    // 한국 시간 2026-10-02 오후 3시. 오늘은 10-01T15:00Z부터 10-02T15:00Z까지다
    private static final Instant NOW = Instant.parse("2026-10-02T06:00:00Z");
    private static final Long USER_ID = 2L;

    @Autowired
    private AdminDashboardQueryService service;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private Clock clock;

    @BeforeEach
    void fixClock() {
        when(clock.instant()).thenReturn(NOW);
    }

    @Test
    @DisplayName("근거를 못 찾은 답변과 실패한 답변을 함께 세고, 실패는 따로도 센다")
    void 답_못한_질문과_실패를_센다() {
        AdminDashboardResponse before = service.getSummary(everyPeriod());
        long sessionId = insertSession();
        insertAnswer(sessionId, 1, "NO_EVIDENCE", "COMPLETED");
        insertAnswer(sessionId, 2, null, "FAILED");
        insertAnswer(sessionId, 3, null, "TIMEOUT");

        AdminDashboardResponse after = service.getSummary(everyPeriod());

        assertThat(after.unansweredCount()).isEqualTo(before.unansweredCount() + 3);
        assertThat(after.failedAnswerCount()).isEqualTo(before.failedAnswerCount() + 2);
    }

    @Test
    @DisplayName("처리한 싫어요는 미처리 수에서 빠진다")
    void 미처리_싫어요만_센다() {
        AdminDashboardResponse before = service.getSummary(everyPeriod());
        long sessionId = insertSession();
        insertDislike(insertAnswer(sessionId, 1, "GROUNDED", "COMPLETED"), false);
        insertDislike(insertAnswer(sessionId, 2, "GROUNDED", "COMPLETED"), true);

        AdminDashboardResponse after = service.getSummary(everyPeriod());

        assertThat(after.unhandledFeedbackCount()).isEqualTo(before.unhandledFeedbackCount() + 1);
    }

    @Test
    @DisplayName("질문은 한국 시간 자정을 기준으로 오늘과 어제로 나뉜다")
    void 질문_수가_한국_시간으로_나뉜다() {
        AdminDashboardResponse before = service.getSummary(everyPeriod());
        long sessionId = insertSession();
        // 한국 시간 오늘 01:00·02:00과 어제 23:00. UTC 날짜로 끊으면 셋 다 10-01이라 같은 날이 된다.
        // 오늘과 어제에 다른 수를 넣어야 두 범위가 뒤바뀐 경우도 잡힌다
        insertQuestion(sessionId, 1, "2026-10-01T16:00:00Z");
        insertQuestion(sessionId, 2, "2026-10-01T17:00:00Z");
        insertQuestion(sessionId, 3, "2026-10-01T14:00:00Z");
        // 한국 시간 그제라 어느 쪽에도 들어가지 않는다
        insertQuestion(sessionId, 4, "2026-09-30T12:00:00Z");

        AdminDashboardResponse after = service.getSummary(everyPeriod());

        assertThat(after.todayQuestionCount()).isEqualTo(before.todayQuestionCount() + 2);
        assertThat(after.yesterdayQuestionCount()).isEqualTo(before.yesterdayQuestionCount() + 1);
    }

    @Test
    @DisplayName("셀 것이 없어도 null이 아니라 0을 반환한다")
    void 값이_없어도_0을_반환한다() {
        AdminDashboardResponse summary = service.getSummary(everyPeriod());

        assertThat(summary.todayQuestionCount()).isNotNegative();
        assertThat(summary.unansweredCount()).isNotNegative();
        assertThat(summary.unhandledFeedbackCount()).isNotNegative();
        assertThat(summary.failedAnswerCount()).isNotNegative();
    }

    @Test
    @DisplayName("기간을 주면 답 못 한 질문과 실패한 답변만 그 기간으로 세고 미처리 싫어요는 전부 센다")
    void 기간은_두_숫자에만_걸린다() {
        AdminDashboardSearchRequest recent =
                new AdminDashboardSearchRequest(Instant.parse("2026-10-01T00:00:00Z"), null);
        AdminDashboardResponse beforeAll = service.getSummary(everyPeriod());
        AdminDashboardResponse beforeRecent = service.getSummary(recent);
        long sessionId = insertSession();
        // 기간 안에 하나, 기간 밖(9월)에 하나. 미처리 싫어요는 기간을 안 따르는지 함께 본다
        insertAnswerAt(sessionId, 1, "NO_EVIDENCE", "COMPLETED", "2026-10-02T03:00:00Z");
        insertAnswerAt(sessionId, 2, "NO_EVIDENCE", "COMPLETED", "2026-09-20T03:00:00Z");
        insertDislike(insertAnswer(sessionId, 3, "GROUNDED", "COMPLETED"), false);

        AdminDashboardResponse afterAll = service.getSummary(everyPeriod());
        AdminDashboardResponse afterRecent = service.getSummary(recent);

        assertThat(afterAll.unansweredCount()).isEqualTo(beforeAll.unansweredCount() + 2);
        assertThat(afterRecent.unansweredCount()).isEqualTo(beforeRecent.unansweredCount() + 1);
        assertThat(afterRecent.unhandledFeedbackCount())
                .isEqualTo(beforeRecent.unhandledFeedbackCount() + 1);
    }

    @Test
    @DisplayName("시작이 끝보다 늦으면 DASHBOARD400-0을 던진다")
    void 거꾸로_된_기간은_막힌다() {
        AdminDashboardSearchRequest reversed = new AdminDashboardSearchRequest(
                Instant.parse("2026-10-02T00:00:00Z"), Instant.parse("2026-10-01T00:00:00Z"));

        assertThatThrownBy(() -> service.getSummary(reversed))
                .isInstanceOf(GeneralException.class)
                .hasFieldOrPropertyWithValue("errorCode", DashboardErrorCode.INVALID_PERIOD);
    }
    
    @Test
    @DisplayName("오늘을 포함한 최근 7일을 오래된 날부터, 기록이 없는 날도 빠짐없이 반환한다")
    void 최근_7일을_빠짐없이_반환한다() {
        AdminDashboardDailyResponse daily = service.getDaily();

        assertThat(daily.days()).extracting(AdminDashboardDailyResponse.Day::date).containsExactly(
                LocalDate.of(2026, 9, 26), LocalDate.of(2026, 9, 27), LocalDate.of(2026, 9, 28),
                LocalDate.of(2026, 9, 29), LocalDate.of(2026, 9, 30), LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 2));
    }

    @Test
    @DisplayName("질문은 한국 시간 자정으로 날짜를 나누고, 7일 밖의 질문은 세지 않는다")
    void 날짜별_질문_수가_한국_시간으로_나뉜다() {
        Map<LocalDate, Long> before = questionsByDay(service.getDaily());
        long sessionId = insertSession();
        // 한국 시간 10-01 23:59:59와 10-02 00:00:00. UTC 날짜로 끊으면 둘 다 10-01이다
        insertQuestion(sessionId, 1, "2026-10-01T14:59:59Z");
        insertQuestion(sessionId, 2, "2026-10-01T15:00:00Z");
        // 한국 시간 09-26 00:00은 첫날에 들어가고, 1초 전인 09-25 23:59:59는 7일 밖이다
        insertQuestion(sessionId, 3, "2026-09-25T15:00:00Z");
        insertQuestion(sessionId, 4, "2026-09-25T14:59:59Z");

        Map<LocalDate, Long> after = questionsByDay(service.getDaily());

        assertThat(after.get(LocalDate.of(2026, 10, 1))).isEqualTo(before.get(LocalDate.of(2026, 10, 1)) + 1);
        assertThat(after.get(LocalDate.of(2026, 10, 2))).isEqualTo(before.get(LocalDate.of(2026, 10, 2)) + 1);
        assertThat(after.get(LocalDate.of(2026, 9, 26))).isEqualTo(before.get(LocalDate.of(2026, 9, 26)) + 1);
        assertThat(after.values().stream().mapToLong(Long::longValue).sum())
                .isEqualTo(before.values().stream().mapToLong(Long::longValue).sum() + 3);
    }

    @Test
    @DisplayName("오류는 오류 목록과 같은 기준으로 세고 성공·근거 없음·취소는 뺀다")
    void 날짜별_오류_수는_오류만_센다() {
        long before = errorsOn(service.getDaily(), LocalDate.of(2026, 10, 2));
        for (String status : new String[] {"TIMEOUT", "CONNECTION_FAILED", "MODEL_ERROR", "SUCCESS", "NO_EVIDENCE", "CANCELLED"}) {
            insertGeneration(status, "2026-10-02T01:00:00Z");
        }

        assertThat(errorsOn(service.getDaily(), LocalDate.of(2026, 10, 2))).isEqualTo(before + 3);
    }

    private Map<LocalDate, Long> questionsByDay(AdminDashboardDailyResponse daily) {
        return daily.days().stream().collect(Collectors.toMap(
                AdminDashboardDailyResponse.Day::date, AdminDashboardDailyResponse.Day::questionCount));
    }

    private long errorsOn(AdminDashboardDailyResponse daily, LocalDate date) {
        return daily.days().stream().filter(day -> day.date().equals(date)).findFirst().orElseThrow().errorCount();
    }

    // 시드의 실행 1번에 붙인다
    private void insertGeneration(String status, String createdAt) {
        entityManager.createNativeQuery(
                        "insert into llm_generations (execution_id, task_type, attempt, model, status, created_at)"
                                + " values (1, 'RAG_ANSWER', 1, 'qwen', :status, cast(:createdAt as timestamptz))")
                .setParameter("status", status)
                .setParameter("createdAt", createdAt)
                .executeUpdate();
    }

    // 기간을 안 주면 전체를 센다
    private AdminDashboardSearchRequest everyPeriod() {
        return new AdminDashboardSearchRequest(null, null);
    }

    private long insertAnswerAt(long sessionId, int sequenceNo, String answerBasis, String status,
            String createdAt) {
        long messageId = insertAnswer(sessionId, sequenceNo, answerBasis, status);
        entityManager.createNativeQuery(
                        "update chat_messages set created_at = cast(:createdAt as timestamptz)"
                                + " where message_id = :messageId")
                .setParameter("createdAt", createdAt)
                .setParameter("messageId", messageId)
                .executeUpdate();
        return messageId;
    }

    private long insertSession() {
        return ((Number) entityManager.createNativeQuery(
                        "insert into chat_sessions (user_id, title) values (:userId, '대시보드 테스트')"
                                + " returning session_id")
                .setParameter("userId", USER_ID)
                .getSingleResult()).longValue();
    }

    private long insertAnswer(long sessionId, int sequenceNo, String answerBasis, String status) {
        return ((Number) entityManager.createNativeQuery(
                        "insert into chat_messages (session_id, sequence_no, role, message_type,"
                                + " content, answer_basis, status)"
                                + " values (:sessionId, :sequenceNo, 'ASSISTANT', 'ANSWER', '답변',"
                                + " :answerBasis, :status) returning message_id")
                .setParameter("sessionId", sessionId)
                .setParameter("sequenceNo", sequenceNo)
                .setParameter("answerBasis", answerBasis)
                .setParameter("status", status)
                .getSingleResult()).longValue();
    }

    private void insertQuestion(long sessionId, int sequenceNo, String createdAt) {
        entityManager.createNativeQuery(
                        "insert into chat_messages (session_id, sequence_no, role, message_type,"
                                + " content, created_at)"
                                + " values (:sessionId, :sequenceNo, 'USER', 'QUESTION', '질문',"
                                + " cast(:createdAt as timestamptz))")
                .setParameter("sessionId", sessionId)
                .setParameter("sequenceNo", sequenceNo)
                .setParameter("createdAt", createdAt)
                .executeUpdate();
    }

    private void insertDislike(long messageId, boolean handled) {
        entityManager.createNativeQuery(
                        "insert into message_feedback (message_id, user_id, rating, reason_code,"
                                + " handled_at, handled_by)"
                                + " values (:messageId, :userId, 'DISLIKE', 'WRONG_INFO',"
                                + " :handledAt, :handledBy)")
                .setParameter("messageId", messageId)
                .setParameter("userId", USER_ID)
                .setParameter("handledAt", handled ? java.sql.Timestamp.from(NOW) : null)
                .setParameter("handledBy", handled ? USER_ID : null)
                .executeUpdate();
    }
}
