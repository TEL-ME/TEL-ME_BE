package com.telme.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.telme.dashboard.dto.res.AdminDashboardResponse;
import com.telme.dashboard.service.AdminDashboardQueryService;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
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
        AdminDashboardResponse before = service.getSummary();
        long sessionId = insertSession();
        insertAnswer(sessionId, 1, "NO_EVIDENCE", "COMPLETED");
        insertAnswer(sessionId, 2, null, "FAILED");
        insertAnswer(sessionId, 3, null, "TIMEOUT");

        AdminDashboardResponse after = service.getSummary();

        assertThat(after.unansweredCount()).isEqualTo(before.unansweredCount() + 3);
        assertThat(after.failedAnswerCount()).isEqualTo(before.failedAnswerCount() + 2);
    }

    @Test
    @DisplayName("처리한 싫어요는 미처리 수에서 빠진다")
    void 미처리_싫어요만_센다() {
        AdminDashboardResponse before = service.getSummary();
        long sessionId = insertSession();
        insertDislike(insertAnswer(sessionId, 1, "GROUNDED", "COMPLETED"), false);
        insertDislike(insertAnswer(sessionId, 2, "GROUNDED", "COMPLETED"), true);

        AdminDashboardResponse after = service.getSummary();

        assertThat(after.unhandledFeedbackCount()).isEqualTo(before.unhandledFeedbackCount() + 1);
    }

    @Test
    @DisplayName("질문은 한국 시간 자정을 기준으로 오늘과 어제로 나뉜다")
    void 질문_수가_한국_시간으로_나뉜다() {
        AdminDashboardResponse before = service.getSummary();
        long sessionId = insertSession();
        // 한국 시간 오늘 01:00·02:00과 어제 23:00. UTC 날짜로 끊으면 셋 다 10-01이라 같은 날이 된다.
        // 오늘과 어제에 다른 수를 넣어야 두 범위가 뒤바뀐 경우도 잡힌다
        insertQuestion(sessionId, 1, "2026-10-01T16:00:00Z");
        insertQuestion(sessionId, 2, "2026-10-01T17:00:00Z");
        insertQuestion(sessionId, 3, "2026-10-01T14:00:00Z");
        // 한국 시간 그제라 어느 쪽에도 들어가지 않는다
        insertQuestion(sessionId, 4, "2026-09-30T12:00:00Z");

        AdminDashboardResponse after = service.getSummary();

        assertThat(after.todayQuestionCount()).isEqualTo(before.todayQuestionCount() + 2);
        assertThat(after.yesterdayQuestionCount()).isEqualTo(before.yesterdayQuestionCount() + 1);
    }

    @Test
    @DisplayName("셀 것이 없어도 null이 아니라 0을 반환한다")
    void 값이_없어도_0을_반환한다() {
        AdminDashboardResponse summary = service.getSummary();

        assertThat(summary.todayQuestionCount()).isNotNegative();
        assertThat(summary.unansweredCount()).isNotNegative();
        assertThat(summary.unhandledFeedbackCount()).isNotNegative();
        assertThat(summary.failedAnswerCount()).isNotNegative();
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
