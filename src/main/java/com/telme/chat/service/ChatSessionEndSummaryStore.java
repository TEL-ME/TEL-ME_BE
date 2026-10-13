package com.telme.chat.service;

import com.telme.chat.config.ChatEndSummaryProperties;
import com.telme.chat.dto.res.ChatSessionEndSummaryResponse;
import com.telme.chat.dto.res.ChatSessionEndSummaryResponse.Content;
import com.telme.chat.exception.ChatErrorCode;
import com.telme.global.common.exception.GeneralException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class ChatSessionEndSummaryStore {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final ChatEndSummaryProperties properties;
    private final ChatSessionEndSummaryGenerator generator;

    @Transactional
    public boolean request(long sessionId) {
        return jdbc.update("""
                INSERT INTO chat_session_end_summaries(session_id, status, created_at, updated_at)
                VALUES (?, 'PENDING', ?, ?) ON CONFLICT (session_id) DO NOTHING
                """, sessionId, ts(clock.instant()), ts(clock.instant())) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    Optional<ChatEndSummaryData.Lease> claim(long sessionId) {
        var rows = jdbc.queryForList("""
                SELECT status, attempt_count, created_at, lease_until
                FROM chat_session_end_summaries WHERE session_id=? FOR UPDATE
                """, sessionId);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        var row = rows.getFirst();
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        String status = (String) row.get("status");
        var until = (java.sql.Timestamp) row.get("lease_until");
        if (!status.equals("PENDING") && (!status.equals("GENERATING")
                || until != null && until.toInstant().isAfter(now))) {
            return Optional.empty();
        }
        Instant created = ((java.sql.Timestamp) row.get("created_at")).toInstant();
        if (!created.plus(properties.maxAge()).isAfter(now)
                || ((Number) row.get("attempt_count")).intValue() >= properties.maxAttempts()) {
            expire(sessionId, now);
            return Optional.empty();
        }
        if (Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM chat_executions WHERE session_id=? AND status='RUNNING')
                """, Boolean.class, sessionId))) {
            return Optional.empty();
        }
        UUID token = UUID.randomUUID();
        Instant deadline = now.plus(properties.leaseDuration());
        if (created.plus(properties.maxAge()).isBefore(deadline)) {
            deadline = created.plus(properties.maxAge());
        }
        int claimed = jdbc.update("""
                UPDATE chat_session_end_summaries r SET status='GENERATING', attempt_count=attempt_count+1,
                    claim_token=?, lease_until=?, updated_at=?, failure_code=NULL
                WHERE session_id=? AND EXISTS (
                    SELECT 1 FROM chat_sessions s WHERE s.session_id=r.session_id AND s.status='CLOSED')
                """, token, ts(deadline), ts(now), sessionId);
        return claimed == 1 ? Optional.of(new ChatEndSummaryData.Lease(sessionId, token, deadline))
                : Optional.empty();
    }

    private void expire(long sessionId, Instant now) {
        jdbc.update("""
                UPDATE chat_session_end_summaries SET status='FAILED', failure_code='WORK_LIMIT',
                    claim_token=NULL, lease_until=NULL, updated_at=? WHERE session_id=?
                """, ts(now), sessionId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    boolean finish(ChatEndSummaryData.Lease lease, ChatEndSummaryData.Input input,
            ChatEndSummaryData.Result result, String status, String failure) {
        Instant now = clock.instant();
        if (status.equals("COMPLETED") && !lease.deadline().isAfter(now)) {
            status = "FAILED";
            result = null;
            failure = "WORK_LIMIT";
        }
        return jdbc.update("""
                UPDATE chat_session_end_summaries SET status=?, result=CAST(? AS jsonb),
                    source_through_sequence_no=?, source_execution_id=?, prompt_version=?,
                    generated_at=?, failure_code=?, claim_token=NULL, lease_until=NULL, updated_at=?
                WHERE session_id=? AND status='GENERATING' AND claim_token=?
                """, status, result == null ? null : result.json(), input == null ? null : input.throughSequence(),
                input == null ? null : input.executionId(), ChatSessionEndSummaryGenerator.VERSION,
                result == null ? null : ts(now), failure, ts(now), lease.sessionId(), lease.token()) == 1;
    }

    List<Long> candidates() {
        return jdbc.queryForList("""
                SELECT session_id FROM chat_session_end_summaries
                WHERE status='PENDING' OR (status='GENERATING' AND lease_until<=?)
                ORDER BY created_at, session_id LIMIT 20
                """, Long.class, ts(clock.instant()));
    }

    @Transactional(readOnly = true)
    public ChatSessionEndSummaryResponse read(ChatActor actor, long sessionId) {
        var session = jdbc.query("""
                SELECT status FROM chat_sessions WHERE session_id=? AND (
                    (CAST(? AS bigint) IS NOT NULL AND user_id=?) OR
                    (CAST(? AS bigint) IS NULL AND user_id IS NULL AND guest_id=?))
                """, (rs, n) -> rs.getString("status"), sessionId,
                actor.userId(), actor.userId(), actor.userId(), actor.guestId());
        if (session.isEmpty()) {
            throw new GeneralException(ChatErrorCode.SESSION_NOT_FOUND);
        }
        if (!properties.enabled()) {
            return response(sessionId, session.getFirst(), "NOT_REQUESTED", null, null);
        }
        var rows = jdbc.query("""
                SELECT status, result::text AS result, generated_at
                FROM chat_session_end_summaries WHERE session_id=?
                """, (rs, n) -> response(sessionId, session.getFirst(), rs.getString("status"),
                        rs.getString("result") == null ? null : generator.readContent(rs.getString("result")),
                        timestamp(rs, "generated_at")), sessionId);
        return rows.isEmpty() ? response(sessionId, session.getFirst(), "NOT_REQUESTED", null, null)
                : rows.getFirst();
    }

    private static java.sql.Timestamp ts(Instant value) {
        return java.sql.Timestamp.from(value);
    }

    private static Instant timestamp(ResultSet rs, String field) throws SQLException {
        var value = rs.getTimestamp(field);
        return value == null ? null : value.toInstant();
    }

    private ChatSessionEndSummaryResponse response(long id, String session, String status,
            Content content, Instant at) {
        String message = switch (status) {
            case "PENDING", "GENERATING" -> "상담 요약을 준비하고 있습니다.";
            case "EMPTY" -> "요약할 상담 내용이 없습니다.";
            case "FAILED" -> "상담 요약을 제공하지 못했습니다. 대화 기록을 확인해 주세요.";
            case "NOT_REQUESTED" -> "이 상담에는 종료 요약이 생성되지 않았습니다.";
            default -> "";
        };
        return new ChatSessionEndSummaryResponse(id, session, status, content, at, message);
    }
}
