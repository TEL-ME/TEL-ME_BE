package com.telme.chat.guard;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.dto.res.ChatMessageSendResponse;
import com.telme.chat.service.ChatActor;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 호출자가 소유권을 검증한 저장 트랜잭션에서만 사용한다. 원문 입력이나 개인정보 값은 받지 않는다. */
@Repository
@RequiredArgsConstructor
public class JdbcInputGuardStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public record State(
            long id,
            Long userId,
            UUID guestId,
            Instant countingFrom,
            Instant restrictionStartedAt,
            Instant restrictionUntil) {}

    public record Cached(String fingerprint, ChatMessageSendResponse response) {}

    public State lock(ChatActor actor, Instant now) {
        Long member = actor.userId();
        if (member == null) {
            member =
                    jdbc.queryForObject(
                            "SELECT merged_user_id FROM guests WHERE guest_id=?",
                            Long.class,
                            actor.guestId());
        }
        if (member != null) {
            jdbc.update(
                    """
                    INSERT INTO chat_input_guard_states(user_id,counting_from_at,updated_at)
                    VALUES (?,to_timestamp(0),?)
                    ON CONFLICT(user_id) WHERE user_id IS NOT NULL DO NOTHING
                    """,
                    member,
                    java.sql.Timestamp.from(now));
            return jdbc.queryForObject(
                    "SELECT * FROM chat_input_guard_states WHERE user_id=? FOR UPDATE",
                    this::state,
                    member);
        }
        jdbc.update(
                """
                INSERT INTO chat_input_guard_states(guest_id,counting_from_at,updated_at)
                VALUES (?,to_timestamp(0),?)
                ON CONFLICT(guest_id) WHERE guest_id IS NOT NULL DO NOTHING
                """,
                actor.guestId(),
                java.sql.Timestamp.from(now));
        return jdbc.queryForObject(
                "SELECT * FROM chat_input_guard_states WHERE guest_id=? FOR UPDATE",
                this::state,
                actor.guestId());
    }

    private State state(ResultSet rs, int row) throws SQLException {
        return new State(
                rs.getLong("guard_state_id"),
                rs.getObject("user_id", Long.class),
                rs.getObject("guest_id", UUID.class),
                rs.getTimestamp("counting_from_at").toInstant(),
                instant(rs, "restriction_started_at"),
                instant(rs, "restriction_until"));
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        var timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    // 조회 경로 JdbcInputGuardStatusStore.findRestrictionEnds와 같은 승계 범위를 유지한다.
    private String cohort(State state) {
        return state.userId() != null
                ? "(s.user_id=? OR s.guest_id IN (SELECT guest_id FROM guests WHERE"
                      + " merged_user_id=?))"
                : "s.guest_id=?";
    }

    private Object[] parameters(State state, Object... first) {
        List<Object> args = new java.util.ArrayList<>(List.of(first));
        if (state.userId() != null) {
            args.add(state.userId());
            args.add(state.userId());
        } else {
            args.add(state.guestId());
        }
        return args.toArray();
    }

    // 본인 제한 우선·승계 제한 선택 정책을 바꾸면 조회 경로와 일치 테스트도 함께 확인한다.
    public State resetExpiredAndInherit(State state, Instant now) {
        if (state.restrictionUntil() != null && !state.restrictionUntil().isAfter(now)) {
            jdbc.update(
                    """
                    UPDATE chat_input_guard_states SET counting_from_at=greatest(counting_from_at,restriction_until),
                        restriction_started_at=NULL,restriction_until=NULL,updated_at=? WHERE guard_state_id=?
                    """,
                    java.sql.Timestamp.from(now),
                    state.id());
            state =
                    jdbc.queryForObject(
                            "SELECT * FROM chat_input_guard_states WHERE guard_state_id=?",
                            this::state,
                            state.id());
        }
        if (state.userId() != null
                && (state.restrictionUntil() == null || !state.restrictionUntil().isAfter(now))) {
            var active =
                    jdbc.query(
                            "SELECT s.* FROM chat_input_guard_states s WHERE s.restriction_until>?"
                                + " AND "
                                    + cohort(state)
                                    + " ORDER BY s.restriction_until DESC LIMIT 1",
                            this::state,
                            parameters(state, java.sql.Timestamp.from(now)));
            if (!active.isEmpty()) {
                State inherited = active.getFirst();
                return restrict(state, inherited.restrictionStartedAt(), inherited.restrictionUntil());
            }
        }
        return state;
    }

    public int count(State state, Instant now, Instant cutoff) {
        return jdbc.queryForObject(
                """
                SELECT count(*) FROM chat_input_guard_events e
                JOIN chat_input_guard_states s ON s.guard_state_id=e.guard_state_id
                WHERE e.counted_violation AND e.detected_at>? AND e.detected_at>=?
                  AND e.detected_at>=greatest(s.counting_from_at,
                    CASE WHEN s.restriction_until<=? THEN s.restriction_until ELSE to_timestamp(0) END)
                  AND
                """
                        + cohort(state),
                Integer.class,
                parameters(
                        state,
                        java.sql.Timestamp.from(cutoff),
                        java.sql.Timestamp.from(state.countingFrom()),
                        java.sql.Timestamp.from(now)));
    }

    public State restrict(State state, Instant started, Instant until) {
        // 저장 전에 DB 정밀도로 맞춰 반올림으로 제한 종료가 뒤로 밀리는 것을 방지한다.
        // 접수 응답·이력 스냅샷에는 RETURNING으로 읽은 실제 저장 시각을 사용한다.
        Instant storedStart = started.truncatedTo(ChronoUnit.MICROS);
        Instant storedUntil = until.truncatedTo(ChronoUnit.MICROS);
        return jdbc.queryForObject(
                "UPDATE chat_input_guard_states SET"
                    + " restriction_started_at=?,restriction_until=?,updated_at=? WHERE"
                    + " guard_state_id=? RETURNING *",
                this::state,
                java.sql.Timestamp.from(storedStart),
                java.sql.Timestamp.from(storedUntil),
                java.sql.Timestamp.from(storedStart),
                state.id());
    }

    public Optional<Cached> cached(State state, UUID requestId) {
        if (requestId == null) {
            return Optional.empty();
        }
        var matches =
                jdbc.query(
                        """
                        SELECT e.request_fingerprint,e.response_snapshot::text FROM chat_input_guard_events e
                        JOIN chat_input_guard_states s ON s.guard_state_id=e.guard_state_id WHERE e.request_id=? AND
                        """
                                + cohort(state),
                        (rs, row) -> new Cached(rs.getString(1), decode(rs.getString(2))),
                        parameters(state, requestId));
        return matches.stream().findFirst();
    }

    public void record(
            State state,
            ChatActor actor,
            long sessionId,
            UUID requestId,
            String fingerprint,
            InputInspection inspection,
            InputGuardNotice notice,
            String policyVersion,
            Instant now,
            ChatMessageSendResponse response) {
        List<String> actions = new java.util.ArrayList<>();
        if (inspection.wasMasked()) {
            actions.add("MASKED");
        }
        if (notice != null && !actions.contains(notice.action())) {
            actions.add(notice.action());
        }
        jdbc.update(
                """
                INSERT INTO chat_input_guard_events(guard_state_id,detected_at,user_id,guest_id,session_id,message_id,
                    request_id,request_fingerprint,sanitized_content,detections,actions,counted_violation,violation_count,
                    restriction_started_at,restriction_until,policy_version,response_snapshot)
                VALUES (?,?,?,?,?,?,?,?,?,?::jsonb,?::jsonb,?,?,?,?,?,?::jsonb)
                """,
                state.id(),
                java.sql.Timestamp.from(now),
                actor.userId(),
                actor.guestId(),
                sessionId,
                response.messageId(),
                requestId,
                requestId == null ? null : fingerprint,
                inspection.content(),
                encode(inspection.detections()),
                encode(actions),
                inspection.hasProfanity(),
                notice == null ? 0 : notice.violationCount(),
                notice == null || notice.restrictionStartedAt() == null
                        ? null
                        : java.sql.Timestamp.from(notice.restrictionStartedAt()),
                notice == null || notice.restrictionUntil() == null
                        ? null
                        : java.sql.Timestamp.from(notice.restrictionUntil()),
                policyVersion,
                encode(response));
    }

    public int purgeBefore(Instant cutoff) {
        return jdbc.update(
                "DELETE FROM chat_input_guard_events WHERE detected_at<?",
                java.sql.Timestamp.from(cutoff));
    }

    private String encode(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("입력 검사 기록을 직렬화할 수 없습니다.");
        }
    }

    private ChatMessageSendResponse decode(String value) {
        try {
            return mapper.readValue(value, ChatMessageSendResponse.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("입력 검사 기록을 읽을 수 없습니다.");
        }
    }
}
