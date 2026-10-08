package com.telme.chat.repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 답 못 한 답변마다 그 상담을 시작한 고객 원문을 찾는다.
 * 되묻기가 있었으면 답변 직전 메시지는 "강남역이요" 같은 조건 답변이라 무엇을 물었는지 알 수 없다.
 * 답변과 상담의 실제 연결을 따라가므로, 상담 없이 끝난 질문은 원문이 비어 있다.
 */
@Repository
@RequiredArgsConstructor
public class UnansweredOriginQuestionFinder {

    private final JdbcTemplate jdbc;

    /** 답변 메시지 ID로 원래 질문을 찾는다. 상담에 이어지지 않는 답변은 결과에 없다. */
    public Map<Long, String> findByAnswerIds(Collection<Long> answerMessageIds) {
        Objects.requireNonNull(answerMessageIds, "answerMessageIds");
        if (answerMessageIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", answerMessageIds.stream().map(id -> "?").toList());
        Map<Long, String> questions = new HashMap<>();
        jdbc.query(
                """
                SELECT answer.message_id, origin.content
                FROM chat_messages answer
                JOIN chat_executions e ON e.output_message_id = answer.message_id
                JOIN chat_messages input ON input.message_id = e.input_message_id
                JOIN LATERAL (
                    -- 그 입력이 상담을 연 질문이면 자기 자신이 원문이다
                    SELECT r.origin_message_id, 0 AS rank, input.sequence_no AS at
                    FROM consult_requests r
                    WHERE r.origin_message_id = input.message_id
                    UNION ALL
                    -- 아니면 그 입력이 되물은 조건의 답으로 기록된 상담을 쓴다.
                    -- 끝난 되묻기가 남아 있어도 이 입력과 이어지지 않으면 고르지 않는다
                    SELECT r.origin_message_id, 1 AS rank, c.condition_id AS at
                    FROM consult_conditions c
                    JOIN consult_requests r ON r.consult_request_id = c.consult_request_id
                    WHERE r.session_id = answer.session_id
                      AND c.answered_message_id = input.message_id
                    ORDER BY rank, at DESC
                    LIMIT 1
                ) picked ON true
                JOIN chat_messages origin ON origin.message_id = picked.origin_message_id
                WHERE answer.message_id IN (%s)
                """.formatted(placeholders),
                rs -> {
                    questions.put(rs.getLong(1), rs.getString(2));
                },
                answerMessageIds.toArray());
        return Map.copyOf(questions);
    }
}
