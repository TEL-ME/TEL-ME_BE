package com.telme.consult.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.consult.dto.ClarificationReask;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Objects;

/** 후속 답변 분석에 사용할 대기 질문 후보. 소유권은 호출자가 먼저 확인한다. */
public final class PendingClarificationFinder {
    private static final TypeReference<List<String>> OPTIONS = new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public PendingClarificationFinder(JdbcTemplate jdbc) {
        this(jdbc, new ObjectMapper());
    }

    public PendingClarificationFinder(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.mapper = Objects.requireNonNull(mapper);
    }

    public record Candidate(
            long consultRequestId,
            String field,
            long questionMessageId,
            String questionText,
            String originalUserQuery,
            String queryText,
            String intent,
            List<String> options,
            int reasks) {

        public Candidate(
                long consultRequestId,
                String field,
                long questionMessageId,
                String questionText,
                String originalUserQuery,
                String queryText,
                String intent,
                List<String> options) {
            this(consultRequestId, field, questionMessageId, questionText, originalUserQuery,
                    queryText, intent, options, 0);
        }

        // 선택지는 FAQ 되묻기에만 붙는다. 매장 되묻기 후보는 선택지가 없다
        public Candidate(
                long consultRequestId,
                String field,
                long questionMessageId,
                String questionText,
                String originalUserQuery,
                String queryText,
                String intent) {
            this(consultRequestId, field, questionMessageId, questionText, originalUserQuery,
                    queryText, intent, List.of());
        }

        public Candidate {
            options = options == null ? List.of() : List.copyOf(options);
        }
    }

    // 후보가 하나여도 새 질문일 수 있다. 답변 연결 여부는 분석 결과로 결정한다.
    public List<Candidate> findBefore(long sessionId, long userMessageId) {
        if (sessionId <= 0 || userMessageId <= 0) {
            throw new IllegalArgumentException("채팅방과 사용자 메시지 ID가 필요합니다.");
        }
        return List.copyOf(
                jdbc.query(
                        """
                        SELECT r.consult_request_id,c.condition_key,q.message_id,q.content,
                               origin.content,r.query_text,r.intent,q.follow_ups,
                               (SELECT count(*) FROM chat_messages re
                                WHERE re.session_id=q.session_id AND re.role='ASSISTANT'
                                  AND re.message_type='CLARIFICATION' AND re.status='COMPLETED'
                                  AND re.sequence_no>q.sequence_no
                                  AND re.content=? || q.content)
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
                                        rs.getString(7),
                                        optionsOf(rs.getString(8)),
                                        rs.getInt(9)),
                        ClarificationReask.prefix(),
                        userMessageId,
                        sessionId));
    }

    // 되묻기 메시지에 실어 보낸 선택지. 형식이 깨졌으면 선택지가 없는 것으로 본다
    private List<String> optionsOf(String followUps) {
        if (followUps == null || followUps.isBlank()) {
            return List.of();
        }
        try {
            return mapper.readValue(followUps, OPTIONS);
        } catch (Exception malformed) {
            return List.of();
        }
    }
}
