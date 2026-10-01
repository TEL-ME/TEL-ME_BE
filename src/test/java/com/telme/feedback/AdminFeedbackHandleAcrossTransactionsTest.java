package com.telme.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.telme.feedback.dto.req.AdminFeedbackHandleRequest;
import com.telme.feedback.dto.res.AdminFeedbackDetailResponse;
import com.telme.feedback.service.AdminFeedbackCommandService;
import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

// updated_at은 트리거가 트랜잭션 시작 시각으로 채워, 한 트랜잭션 안에서는 값이 변하지 않는다.
// 요청마다 트랜잭션이 나뉘는 실제 흐름을 보려고 @Transactional을 쓰지 않고 남은 행을 직접 지운다
@SpringBootTest
class AdminFeedbackHandleAcrossTransactionsTest {

    private static final String MARK = "처리시각확인용";
    private static final long ANSWER_MESSAGE_ID = 2L;
    private static final long ADMIN_ID = 2L;
    private static final long OTHER_USER_ID = 849L;

    @Autowired
    private AdminFeedbackCommandService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long feedbackId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update(
                "INSERT INTO message_feedback (message_id, user_id, rating, reason_code, comment)"
                        + " VALUES (?, ?, 'DISLIKE', 'WRONG_INFO', ?)",
                ANSWER_MESSAGE_ID, OTHER_USER_ID, MARK);
        feedbackId = jdbcTemplate.queryForObject(
                "SELECT feedback_id FROM message_feedback WHERE message_id = ? AND user_id = ?",
                Long.class, ANSWER_MESSAGE_ID, OTHER_USER_ID);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM message_feedback WHERE feedback_id = ?", feedbackId);
    }

    @Test
    @DisplayName("처리 응답의 updatedAt은 저장된 값과 같다")
    void 응답의_수정시각은_저장된_값이다() {
        AdminFeedbackDetailResponse response =
                service.changeHandled(feedbackId, request("처리함", null), ADMIN_ID);

        Timestamp saved = jdbcTemplate.queryForObject(
                "SELECT updated_at FROM message_feedback WHERE feedback_id = ?", Timestamp.class, feedbackId);
        assertThat(response.updatedAt()).isEqualTo(saved.toInstant());
    }

    @Test
    @DisplayName("처리 응답의 updatedAt으로 바로 다시 처리해도 막히지 않는다")
    void 응답값을_그대로_다시_보내도_통과한다() {
        Instant updatedAt = service.changeHandled(feedbackId, request("처리함", null), ADMIN_ID).updatedAt();

        assertThatCode(() -> service.changeHandled(feedbackId, request("메모만 고침", updatedAt), ADMIN_ID))
                .doesNotThrowAnyException();
    }

    private AdminFeedbackHandleRequest request(String note, Instant updatedAt) {
        return new AdminFeedbackHandleRequest(true, note, updatedAt);
    }
}
