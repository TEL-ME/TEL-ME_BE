package com.telme.feedback.repository;

import com.telme.feedback.dto.FeedbackModels;
import com.telme.feedback.dto.FeedbackModels.Actor;
import com.telme.feedback.dto.FeedbackModels.Feedback;
import com.telme.feedback.dto.FeedbackModels.Input;
import com.telme.feedback.dto.FeedbackModels.Rating;
import com.telme.feedback.dto.FeedbackModels.Reason;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/** PR #7 테이블을 그대로 이용한다. 신규 테이블·엔티티 없음. 인증 통합 전 자동 등록하지 않는다. */
public final class JdbcFeedbackStore implements FeedbackStore {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public JdbcFeedbackStore(JdbcTemplate jdbc, TransactionTemplate tx) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.tx = Objects.requireNonNull(tx);
    }

    @Override
    public Feedback upsert(long messageId, Actor actor, Input input) {
        return tx.execute(
                status -> {
                    checkTarget(messageId, actor, true);
                    String column = actor.userId() != null ? "user_id" : "guest_id";
                    // 고정된 두 컬럼명만 선택한다. 사용자 입력 SQL이 아니다.
                    String sql =
                            "INSERT INTO message_feedback"
                                + " (message_id,user_id,guest_id,rating,reason_code,comment) VALUES"
                                + " (?,?,?,?,?,?) ON CONFLICT (message_id,"
                                    + column
                                    + ") WHERE "
                                    + column
                                    + " IS NOT NULL DO UPDATE SET"
                                    + " rating=EXCLUDED.rating,reason_code=EXCLUDED.reason_code,comment=EXCLUDED.comment,updated_at=now()"
                                    + " RETURNING *";
                    return jdbc.queryForObject(
                            sql,
                            this::read,
                            messageId,
                            actor.userId(),
                            actor.guestId(),
                            input.rating().name(),
                            input.reason() == null ? null : input.reason().name(),
                            input.comment());
                });
    }

    @Override
    public Optional<Feedback> find(long messageId, Actor actor) {
        return tx.execute(
                status -> {
                    // 소유권과 평가를 한 쿼리에서 읽는다. 조회는 채팅 행을 잠그지 않는다.
                    String ownerFilter =
                            actor.userId() != null
                                    ? "s.user_id=?"
                                    : "s.user_id IS NULL AND s.guest_id=?";
                    List<Feedback> rows =
                            jdbc.query(
                                    "SELECT f.* FROM chat_messages m JOIN chat_sessions s ON"
                                        + " s.session_id=m.session_id LEFT JOIN message_feedback f"
                                        + " ON f.message_id=m.message_id AND f."
                                            + actorColumn(actor)
                                            + "=? WHERE m.message_id=? AND "
                                            + ownerFilter,
                                    (rs, n) ->
                                            rs.getObject("feedback_id") == null
                                                    ? null
                                                    : read(rs, n),
                                    actorId(actor),
                                    messageId,
                                    actorId(actor));
                    if (rows.isEmpty()) {
                        throw new TargetUnavailable();
                    }
                    return Optional.ofNullable(rows.getFirst());
                });
    }

    @Override
    public void succeedGuestFeedback(UUID guestId, long userId) {
        // guest_id는 이력 보존을 위해 유지한다. chat_sessions 승계(succeedGuestSessions)와 같은 방식.
        tx.executeWithoutResult(
                status ->
                        jdbc.update(
                                "UPDATE message_feedback SET user_id=? WHERE guest_id=? AND"
                                        + " user_id IS NULL",
                                userId,
                                guestId));
    }
    @Override
    public Map<Long, Feedback> findByMessageIds(List<Long> messageIds, Actor actor) {
        if (messageIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", Collections.nCopies(messageIds.size(), "?"));
        String sql = "SELECT * FROM message_feedback WHERE message_id IN (" + placeholders
                + ") AND " + actorColumn(actor) + "=?";
        Object[] params = new Object[messageIds.size() + 1];
        for (int i = 0; i < messageIds.size(); i++) {
            params[i] = messageIds.get(i);
        }
        params[messageIds.size()] = actorId(actor);
        return jdbc.query(sql, this::read, params).stream()
                .collect(Collectors.toMap(Feedback::messageId, f -> f));
    }

    @Override
    public void delete(long messageId, Actor actor) {
        tx.executeWithoutResult(
                status -> {
                    checkTarget(messageId, actor, false);
                    jdbc.update(
                            "DELETE FROM message_feedback WHERE message_id=? AND "
                                    + actorColumn(actor)
                                    + "=?",
                            messageId,
                            actorId(actor));
                });
    }

    private void checkTarget(long id, Actor actor, boolean requireAnswer) {
        // 세션 소유권 변경·삭제와 피드백 저장의 경쟁을 줄이기 위해 같은 트랜잭션에서 잠근다.
        var targets =
                jdbc.query(
                        "SELECT s.user_id,s.guest_id,m.role,m.message_type,m.status FROM"
                            + " chat_messages m JOIN chat_sessions s ON s.session_id=m.session_id"
                            + " WHERE m.message_id=? FOR UPDATE OF s,m",
                        (rs, n) ->
                                new Target(
                                        rs.getObject("user_id", Long.class),
                                        rs.getObject("guest_id", UUID.class),
                                        rs.getString("role"),
                                        rs.getString("message_type"),
                                        rs.getString("status")),
                        id);
        if (targets.isEmpty()) {
            throw new TargetUnavailable();
        }
        var t = targets.getFirst();
        // 회원 연결 후 guest_id가 남더라도 과거 게스트에게 접근을 허용하지 않는다.
        boolean owns =
                actor.userId() != null
                        ? actor.userId().equals(t.userId())
                        : t.userId() == null && actor.guestId().equals(t.guestId());
        if (!owns) {
            throw new TargetUnavailable();
        }
        if (requireAnswer
                && !FeedbackModels.isRatable(t.role(), t.type(), t.status())) {
            throw new TargetNotReady();
        }
    }

    private record Target(Long userId, UUID guestId, String role, String type, String status) {}

    private String actorColumn(Actor actor) {
        return actor.userId() != null ? "user_id" : "guest_id";
    }

    private Object actorId(Actor actor) {
        return actor.userId() != null ? actor.userId() : actor.guestId();
    }

    private Feedback read(ResultSet rs, int n) throws SQLException {
        var reason = rs.getString("reason_code");
     // 로그인 승계 후에는 user_id와 guest_id가 함께 남으므로 회원 신원을 우선한다.
        var userId = rs.getObject("user_id", Long.class);
        return new Feedback(
                rs.getLong("feedback_id"),
                rs.getLong("message_id"),
                userId != null ? new Actor(userId, null) : new Actor(null, 
                        rs.getObject("guest_id", UUID.class)),
                new Input(
                        Rating.valueOf(rs.getString("rating")),
                        reason == null ? null : Reason.valueOf(reason),
                        rs.getString("comment")),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }
}
