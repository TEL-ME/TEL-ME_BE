package com.telme.feedback.repository;

import com.telme.feedback.dto.FeedbackModels.*;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

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
                    checkTarget(messageId, actor, false);
                    List<Feedback> rows =
                            jdbc.query(
                                    "SELECT * FROM message_feedback WHERE message_id=? AND "
                                            + actorColumn(actor)
                                            + "=?",
                                    this::read,
                                    messageId,
                                    actorId(actor));
                    return rows.stream().findFirst();
                });
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
        if (targets.isEmpty()) throw new TargetUnavailable();
        var t = targets.getFirst();
        // 회원 연결 후 guest_id가 남더라도 과거 게스트에게 접근을 허용하지 않는다.
        boolean owns =
                actor.userId() != null
                        ? actor.userId().equals(t.userId())
                        : t.userId() == null && actor.guestId().equals(t.guestId());
        if (!owns) throw new TargetUnavailable();
        if (requireAnswer
                && !("ASSISTANT".equals(t.role())
                        && "ANSWER".equals(t.type())
                        && "COMPLETED".equals(t.status()))) throw new TargetNotReady();
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
        return new Feedback(
                rs.getLong("feedback_id"),
                rs.getLong("message_id"),
                new Actor(
                        rs.getObject("user_id", Long.class), rs.getObject("guest_id", UUID.class)),
                new Input(
                        Rating.valueOf(rs.getString("rating")),
                        reason == null ? null : Reason.valueOf(reason),
                        rs.getString("comment")),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }
}
