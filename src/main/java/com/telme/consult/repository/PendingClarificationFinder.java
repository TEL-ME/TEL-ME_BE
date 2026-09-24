package com.telme.consult.repository;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Objects;

/** 후속 답변 분석에 사용할 대기 질문 후보. 소유권은 호출자가 먼저 확인한다. */
public final class PendingClarificationFinder {
    private final JdbcTemplate jdbc;

    public PendingClarificationFinder(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    public record Candidate(
            long consultRequestId,
            String field,
            long questionMessageId,
            String questionText,
            String originalUserQuery,
            String queryText,
            String intent) {}

    // 후보가 하나여도 새 질문일 수 있다. 답변 연결 여부는 분석 결과로 결정한다.
    public List<Candidate> findBefore(long sessionId, long userMessageId) {
        if (sessionId <= 0 || userMessageId <= 0) {
            throw new IllegalArgumentException("채팅방과 사용자 메시지 ID가 필요합니다.");
        }
        return List.copyOf(
                jdbc.query(
                        """
                        SELECT r.consult_request_id,c.condition_key,q.message_id,q.content,
                               origin.content,r.query_text,r.intent
                        FROM consult_requests r
                        JOIN consult_conditions c ON c.consult_request_id=r.consult_request_id
                        JOIN chat_messages q ON q.message_id=c.asked_message_id
                        JOIN chat_messages origin ON origin.message_id=r.origin_message_id
                        JOIN chat_messages a ON a.message_id=? AND a.session_id=r.session_id
                        WHERE r.session_id=? AND r.status='WAITING_CONDITION'
                          AND c.status='PENDING' AND c.answered_message_id IS NULL
                          AND q.session_id=r.session_id AND q.role='ASSISTANT'
                          AND q.message_type='CLARIFICATION' AND q.status='COMPLETED'
                          AND a.role='USER' AND a.status='COMPLETED' AND q.sequence_no<a.sequence_no
                        ORDER BY q.sequence_no DESC,r.subquery_order ASC,r.consult_request_id ASC,c.condition_key ASC
                        """,
                        (rs, n) ->
                                new Candidate(
                                        rs.getLong(1),
                                        rs.getString(2),
                                        rs.getLong(3),
                                        rs.getString(4),
                                        rs.getString(5),
                                        rs.getString(6),
                                        rs.getString(7)),
                        userMessageId,
                        sessionId));
    }
}
