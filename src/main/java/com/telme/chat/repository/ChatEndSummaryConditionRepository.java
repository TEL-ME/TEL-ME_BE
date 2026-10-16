package com.telme.chat.repository;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ChatEndSummaryConditionRepository {
    private final JdbcTemplate jdbc;

    public record ConditionRow(long requestId, long originMessageId, String key, String value, String status,
            String source, Long answeredMessageId, Integer answeredSequence, Long askedMessageId) {}

    public List<ConditionRow> findBySessionId(long sessionId) {
        return jdbc.query("""
                SELECT r.consult_request_id, r.origin_message_id, c.condition_key, c.condition_value,
                    c.status, c.source, c.answered_message_id, a.sequence_no, c.asked_message_id
                FROM consult_requests r JOIN consult_conditions c USING (consult_request_id)
                LEFT JOIN chat_messages a ON a.message_id=c.answered_message_id AND a.session_id=r.session_id
                WHERE r.session_id=? ORDER BY r.consult_request_id, c.condition_key
                """, (rs, n) -> new ConditionRow(rs.getLong("consult_request_id"), rs.getLong("origin_message_id"),
                        rs.getString("condition_key"), rs.getString("condition_value"), rs.getString("status"),
                        rs.getString("source"), (Long) rs.getObject("answered_message_id"),
                        (Integer) rs.getObject("sequence_no"), (Long) rs.getObject("asked_message_id")), sessionId);
    }
}
