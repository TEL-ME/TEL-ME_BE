package com.telme.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.telme.feedback.dto.req.AdminFeedbackHandledFilter;
import com.telme.feedback.dto.req.AdminFeedbackSearchRequest;
import com.telme.feedback.dto.res.AdminFeedbackDetailResponse;
import com.telme.feedback.dto.res.AdminFeedbackListItemResponse;
import com.telme.feedback.dto.res.AdminFeedbackListResponse;
import com.telme.feedback.entity.MessageFeedback;
import com.telme.feedback.service.AdminFeedbackQueryService;
import com.telme.global.common.exception.GeneralException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

// 시드에 있는 답변 메시지에 평가를 붙여 조건을 확인한다. 트랜잭션 롤백으로 시드에 영향 없음
@SpringBootTest
@Transactional
class AdminFeedbackQueryServiceTest {

    // 시드의 ASSISTANT 답변. 2번은 1번 질문에, 4번은 3번 질문에 달려 있다
    private static final long ANSWER_WITH_PLAN_QUESTION = 2L;
    private static final long ANSWER_WITH_STORE_QUESTION = 4L;
    private static final long ADMIN_ID = 2L;
    private static final long USER_ID = 1L;

    @Autowired
    private AdminFeedbackQueryService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long unhandledId;
    private Long handledId;

    @BeforeEach
    void setUp() {
        unhandledId = insertDislike(
                ANSWER_WITH_PLAN_QUESTION, ADMIN_ID, "WRONG_INFO", "답이 틀렸습니다.", false);
        handledId = insertDislike(
                ANSWER_WITH_STORE_QUESTION, USER_ID, "NOT_RELATED", "물어본 것과 다릅니다.", true);
    }

    @Test
    @DisplayName("조건 없이 조회하면 처리한 건은 빠진다")
    void 기본은_미처리만_본다() {
        List<Long> ids = ids(service.getDislikes(request(null, null, null, null)));

        assertThat(ids).contains(unhandledId).doesNotContain(handledId);
    }

    @Test
    @DisplayName("처리된 것만 보면 미처리가 빠진다")
    void 처리된_것만_본다() {
        List<Long> ids = ids(service.getDislikes(
                request(null, AdminFeedbackHandledFilter.HANDLED, null, null)));

        assertThat(ids).contains(handledId).doesNotContain(unhandledId);
    }

    @Test
    @DisplayName("사유를 주면 그 사유만 남는다")
    void 사유로_거른다() {
        AdminFeedbackListResponse response = service.getDislikes(request(
                MessageFeedback.ReasonCode.WRONG_INFO, AdminFeedbackHandledFilter.ALL, null, null));

        assertThat(ids(response)).contains(unhandledId).doesNotContain(handledId);
        assertThat(response.feedbacks()).allMatch(item -> "WRONG_INFO".equals(item.reasonCode()));
    }

    @Test
    @DisplayName("기간을 주면 그 밖의 평가는 빠진다")
    void 기간으로_거른다() {
        Instant now = Instant.now();

        List<Long> recent = ids(service.getDislikes(request(
                null, AdminFeedbackHandledFilter.ALL, now.minus(1, ChronoUnit.HOURS), null)));
        List<Long> old = ids(service.getDislikes(request(
                null, AdminFeedbackHandledFilter.ALL, null, now.minus(1, ChronoUnit.HOURS))));

        assertThat(recent).contains(unhandledId, handledId);
        assertThat(old).doesNotContain(unhandledId, handledId);
    }

    @Test
    @DisplayName("목록에 사용자가 물어본 질문이 함께 나온다")
    void 질문을_함께_보여준다() {
        AdminFeedbackListResponse response = service.getDislikes(request(null, null, null, null));

        AdminFeedbackListItemResponse item = response.feedbacks().stream()
                .filter(each -> each.feedbackId().equals(unhandledId))
                .findFirst()
                .orElseThrow();
        assertThat(item.questionPreview()).isEqualTo("요금제 변경하고 싶어요");
        assertThat(item.commentPreview()).isEqualTo("답이 틀렸습니다.");
    }

    @Test
    @DisplayName("기간의 시작이 끝보다 늦으면 FEEDBACK400-0을 던진다")
    void 뒤집힌_기간은_막는다() {
        Instant now = Instant.now();

        assertThatThrownBy(() -> service.getDislikes(
                request(null, null, now, now.minus(1, ChronoUnit.DAYS))))
                .isInstanceOf(GeneralException.class);
    }

    @Test
    @DisplayName("상세에는 질문·답변·근거 FAQ·사용자 의견이 함께 나온다")
    void 상세를_본다() {
        AdminFeedbackDetailResponse detail = service.getDislike(unhandledId);

        assertThat(detail.question()).isEqualTo("요금제 변경하고 싶어요");
        assertThat(detail.answer()).startsWith("요금제는 매월 1회 변경");
        assertThat(detail.answerBasis()).isEqualTo("GROUNDED");
        assertThat(detail.comment()).isEqualTo("답이 틀렸습니다.");
        assertThat(detail.handled()).isFalse();
        // 시드의 2번 답변은 1번 FAQ를 근거로 쓴다
        assertThat(detail.sources()).singleElement()
                .satisfies(source -> {
                    assertThat(source.faqId()).isEqualTo(1L);
                    assertThat(source.searchRank()).isEqualTo((short) 1);
                });
    }

    @Test
    @DisplayName("처리한 건은 처리자와 처리 시각이 함께 나온다")
    void 처리_정보를_본다() {
        AdminFeedbackDetailResponse detail = service.getDislike(handledId);

        assertThat(detail.handled()).isTrue();
        assertThat(detail.handledBy()).isEqualTo(ADMIN_ID);
        assertThat(detail.handledAt()).isNotNull();
    }

    @Test
    @DisplayName("근거로 쓴 FAQ가 없으면 빈 목록으로 나온다")
    void 근거가_없으면_빈_목록이다() {
        // 시드의 4번 답변은 매장 추천이라 근거 FAQ가 없다
        AdminFeedbackDetailResponse detail = service.getDislike(handledId);

        assertThat(detail.sources()).isEmpty();
    }

    @Test
    @DisplayName("좋아요 id로 상세를 부르면 FEEDBACK404-1을 던진다")
    void 좋아요는_상세에서_빠진다() {
        Long likeId = jdbcTemplate.queryForObject(
                "SELECT feedback_id FROM message_feedback WHERE rating = 'LIKE' LIMIT 1", Long.class);

        assertThatThrownBy(() -> service.getDislike(likeId)).isInstanceOf(GeneralException.class);
    }

    @Test
    @DisplayName("없는 id로 상세를 부르면 FEEDBACK404-1을 던진다")
    void 없는_id는_막는다() {
        assertThatThrownBy(() -> service.getDislike(-1L)).isInstanceOf(GeneralException.class);
    }

    private Long insertDislike(long messageId, long userId, String reason, String comment, boolean handled) {
        jdbcTemplate.update(
                "INSERT INTO message_feedback "
                        + "(message_id, user_id, rating, reason_code, comment, handled_at, handled_by) "
                        + "VALUES (?, ?, 'DISLIKE', ?, ?, ?, ?)",
                messageId, userId, reason, comment,
                handled ? java.sql.Timestamp.from(Instant.now()) : null,
                handled ? ADMIN_ID : null);
        return jdbcTemplate.queryForObject(
                "SELECT feedback_id FROM message_feedback WHERE message_id = ? AND user_id = ?",
                Long.class, messageId, userId);
    }

    private List<Long> ids(AdminFeedbackListResponse response) {
        return response.feedbacks().stream().map(AdminFeedbackListItemResponse::feedbackId).toList();
    }

    private AdminFeedbackSearchRequest request(
            MessageFeedback.ReasonCode reason, AdminFeedbackHandledFilter handled, Instant from, Instant to) {
        return new AdminFeedbackSearchRequest(reason, handled, from, to, null, 100);
    }
}
