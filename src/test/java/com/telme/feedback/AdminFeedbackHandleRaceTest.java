package com.telme.feedback;

import static org.assertj.core.api.Assertions.assertThat;

import com.telme.feedback.dto.FeedbackModels.Actor;
import com.telme.feedback.dto.FeedbackModels.Input;
import com.telme.feedback.dto.FeedbackModels.Rating;
import com.telme.feedback.dto.FeedbackModels.Reason;
import com.telme.feedback.dto.req.AdminFeedbackHandleRequest;
import com.telme.feedback.repository.FeedbackStore;
import com.telme.feedback.service.AdminFeedbackCommandService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = "telme.feedback.enabled=true")
class AdminFeedbackHandleRaceTest {

    @Autowired AdminFeedbackCommandService service;
    @Autowired FeedbackStore store;
    @Autowired JdbcTemplate jdbcTemplate;

    long userId;
    long sessionId;
    long answerId;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM message_feedback WHERE message_id = ?", answerId);
        jdbcTemplate.update("DELETE FROM chat_messages WHERE session_id = ?", sessionId);
        jdbcTemplate.update("DELETE FROM chat_sessions WHERE session_id = ?", sessionId);
        jdbcTemplate.update("DELETE FROM users WHERE user_id = ?", userId);
    }

    // 두 요청의 순서는 고를 수 없어 여러 번 돌려 둘 다 나오게 한다
    @RepeatedTest(5)
    @DisplayName("확인과 저장 사이에 사용자가 고쳐도 안 읽은 의견이 처리 완료로 남지 않는다")
    void 확인과_저장_사이에_사용자가_고쳐도_안_읽은_내용이_처리되지_않는다() throws Exception {
        userId = jdbcTemplate.queryForObject(
                "INSERT INTO users(name) VALUES ('race owner') RETURNING user_id", Long.class);
        sessionId = jdbcTemplate.queryForObject(
                "INSERT INTO chat_sessions(user_id) VALUES (?) RETURNING session_id", Long.class, userId);
        answerId = jdbcTemplate.queryForObject(
                "INSERT INTO chat_messages(session_id,sequence_no,role,message_type,status,content)"
                        + " VALUES (?,1,'ASSISTANT','ANSWER','COMPLETED','답변') RETURNING message_id",
                Long.class, sessionId);
        store.upsert(answerId, new Actor(userId, null), new Input(Rating.DISLIKE, Reason.WRONG_INFO, "원래 의견"));
        Long feedbackId = jdbcTemplate.queryForObject(
                "SELECT feedback_id FROM message_feedback WHERE message_id = ?", Long.class, answerId);
        Instant opened = jdbcTemplate.queryForObject(
                "SELECT updated_at FROM message_feedback WHERE feedback_id = ?", Timestamp.class, feedbackId)
                .toInstant();

        CountDownLatch go = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        var admin = pool.submit(() -> {
            go.await();
            try {
                service.changeHandled(feedbackId, new AdminFeedbackHandleRequest(true, "처리함", opened), 2L);
                return "처리 완료";
            } catch (Exception e) {
                return e.getClass().getSimpleName();
            }
        });
        pool.submit(() -> {
            go.await();
            return store.upsert(answerId, new Actor(userId, null),
                    new Input(Rating.DISLIKE, Reason.NOT_RELATED, "사용자가 고친 의견"));
        });
        go.countDown();
        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT comment, handled_at IS NOT NULL AS handled FROM message_feedback WHERE feedback_id = ?",
                feedbackId);
        System.out.println("관리자=" + admin.get() + " 최종=" + row);

        // 순서와 상관없이, 관리자가 읽지 않은 새 의견이 처리 완료로 남으면 안 된다
        boolean unreviewedButHandled =
                "사용자가 고친 의견".equals(row.get("comment")) && Boolean.TRUE.equals(row.get("handled"));
        assertThat(unreviewedButHandled).isFalse();
    }
}
