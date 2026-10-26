package com.telme.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.telme.feedback.dto.req.AdminFeedbackHandleRequest;
import com.telme.feedback.dto.res.AdminFeedbackDetailResponse;
import com.telme.feedback.entity.MessageFeedback;
import com.telme.feedback.service.AdminFeedbackCommandService;
import com.telme.global.common.exception.GeneralException;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

// 시드의 답변 메시지에 싫어요를 붙여 확인한다. 트랜잭션 롤백으로 시드에 영향 없음
@SpringBootTest
@Transactional
class AdminFeedbackCommandServiceTest {

    private static final long ANSWER_MESSAGE_ID = 2L;
    private static final long ADMIN_ID = 2L;

    @Autowired
    private AdminFeedbackCommandService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private Long feedbackId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update(
                "INSERT INTO message_feedback (message_id, user_id, rating, reason_code, comment) "
                        + "VALUES (?, ?, 'DISLIKE', 'WRONG_INFO', '답이 틀렸습니다.')",
                ANSWER_MESSAGE_ID, ADMIN_ID);
        feedbackId = jdbcTemplate.queryForObject(
                "SELECT feedback_id FROM message_feedback WHERE message_id = ? AND user_id = ?",
                Long.class, ANSWER_MESSAGE_ID, ADMIN_ID);
    }

    @Test
    @DisplayName("처리 표시를 하면 시각·처리자·메모가 DB에 남는다")
    void 처리하면_DB에_남는다() {
        AdminFeedbackDetailResponse detail =
                service.changeHandled(feedbackId, request(true, "FAQ 수정 완료"), ADMIN_ID);

        assertThat(detail.handled()).isTrue();
        assertThat(detail.handledBy()).isEqualTo(ADMIN_ID);
        assertThat(detail.handledNote()).isEqualTo("FAQ 수정 완료");
        assertThat(saved()).containsExactly(true, ADMIN_ID, "FAQ 수정 완료");
    }

    @Test
    @DisplayName("되돌리면 처리 정보가 모두 비워진다")
    void 되돌리면_비워진다() {
        service.changeHandled(feedbackId, request(true, "처리함"), ADMIN_ID);

        AdminFeedbackDetailResponse detail = service.changeHandled(feedbackId, request(false, null), ADMIN_ID);

        assertThat(detail.handled()).isFalse();
        assertThat(detail.handledAt()).isNull();
        assertThat(saved()).containsExactly(false, null, null);
    }

    @Test
    @DisplayName("이미 처리한 건을 다시 보내면 메모만 바뀐다")
    void 다시_보내면_메모가_바뀐다() {
        service.changeHandled(feedbackId, request(true, "처음 메모"), ADMIN_ID);

        AdminFeedbackDetailResponse detail =
                service.changeHandled(feedbackId, request(true, "고친 메모"), ADMIN_ID);

        assertThat(detail.handledNote()).isEqualTo("고친 메모");
    }

    @Test
    @DisplayName("좋아요 id로 처리 표시를 하면 FEEDBACK404-1을 던진다")
    void 좋아요는_막는다() {
        Long likeId = jdbcTemplate.queryForObject(
                "SELECT feedback_id FROM message_feedback WHERE rating = 'LIKE' LIMIT 1", Long.class);

        assertThatThrownBy(() -> service.changeHandled(likeId, request(true, null), ADMIN_ID))
                .isInstanceOf(GeneralException.class);
    }

    @Test
    @DisplayName("없는 id로 처리 표시를 하면 FEEDBACK404-1을 던진다")
    void 없는_id는_막는다() {
        assertThatThrownBy(() -> service.changeHandled(-1L, request(true, null), ADMIN_ID))
                .isInstanceOf(GeneralException.class);
    }

    @Test
    @DisplayName("읽은 뒤 사용자가 좋아요로 바꿨으면 FEEDBACK409-1을 던진다")
    void 그_사이_좋아요가_되면_막는다() {
        entityManager.flush();
        entityManager.find(MessageFeedback.class, feedbackId);
        userChanges("UPDATE message_feedback SET rating='LIKE', reason_code=NULL, comment=NULL WHERE feedback_id=?");

        assertThatThrownBy(() -> service.changeHandled(feedbackId, request(true, "처리함"), ADMIN_ID))
                .isInstanceOf(GeneralException.class);
    }

    @Test
    @DisplayName("읽은 뒤 사용자가 고친 의견을 처리 표시가 덮어쓰지 않는다")
    void 그_사이_고친_의견을_지키다() {
        entityManager.flush();
        entityManager.find(MessageFeedback.class, feedbackId);
        userChanges("UPDATE message_feedback SET comment='사용자가 고친 의견' WHERE feedback_id=?");

        service.changeHandled(feedbackId, request(true, "처리함"), ADMIN_ID);

        entityManager.flush();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT comment FROM message_feedback WHERE feedback_id = ?", String.class, feedbackId))
                .isEqualTo("사용자가 고친 의견");
    }

    @Test
    @DisplayName("상세에서 받은 수정 시각을 그대로 보내면 처리된다")
    void 맞는_수정_시각은_통과한다() {
        entityManager.flush();
        Instant updatedAt = service.changeHandled(feedbackId, request(false, null), ADMIN_ID).updatedAt();

        AdminFeedbackDetailResponse detail =
                service.changeHandled(feedbackId, requestWithUpdatedAt(updatedAt, "처리함"), ADMIN_ID);

        assertThat(detail.handled()).isTrue();
    }

    @Test
    @DisplayName("상세를 본 뒤 사용자가 고쳤으면 FEEDBACK409-1을 던진다")
    void 읽은_뒤_고쳐졌으면_막는다() {
        entityManager.flush();
        Instant opened = service.changeHandled(feedbackId, request(false, null), ADMIN_ID).updatedAt();

        // 사용자가 고치면 트리거가 updated_at을 올린다. 테스트는 한 트랜잭션이라
        // now()가 고정돼 값이 안 변하므로, 화면이 낡은 값을 들고 있는 상태를 직접 만든다
        Instant beforeUserEdit = opened.minusSeconds(60);

        assertThatThrownBy(() ->
                        service.changeHandled(feedbackId, requestWithUpdatedAt(beforeUserEdit, "처리함"), ADMIN_ID))
                .isInstanceOf(GeneralException.class);
    }

    @Test
    @DisplayName("읽은 뒤 사용자가 평가를 취소했으면 FEEDBACK404-1을 던진다")
    void 읽은_뒤_취소됐으면_없는_것으로_본다() {
        entityManager.flush();
        entityManager.find(MessageFeedback.class, feedbackId);
        userChanges("DELETE FROM message_feedback WHERE feedback_id = ?");

        assertThatThrownBy(() -> service.changeHandled(feedbackId, request(true, "처리함"), ADMIN_ID))
                .isInstanceOf(GeneralException.class);
    }

    // 관리자가 읽어둔 뒤 사용자가 DB를 바꾼 상황을 만든다.
    // JPA를 거치지 않고 바꿔야 관리자 쪽 엔티티가 옛 값을 들고 있게 된다
    private void userChanges(String sql) {
        jdbcTemplate.update(sql, feedbackId);
    }

    // 영속성 컨텍스트가 아니라 DB에 실제로 나갔는지 본다
    private java.util.List<Object> saved() {
        entityManager.flush();
        return jdbcTemplate.queryForObject(
                "SELECT handled_at, handled_by, handled_note FROM message_feedback WHERE feedback_id = ?",
                (rs, row) -> java.util.Arrays.asList(
                        rs.getTimestamp("handled_at") != null,
                        (Long) rs.getObject("handled_by"),
                        rs.getString("handled_note")),
                feedbackId);
    }

    private AdminFeedbackHandleRequest requestWithUpdatedAt(Instant updatedAt, String note) {
        return new AdminFeedbackHandleRequest(true, note, updatedAt);
    }

    private AdminFeedbackHandleRequest request(boolean handled, String note) {
        return new AdminFeedbackHandleRequest(handled, note, null);
    }
}
