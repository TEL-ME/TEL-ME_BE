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
 */
@Repository
@RequiredArgsConstructor
public class UnansweredOriginQuestionFinder {

    private final JdbcTemplate jdbc;

    /** 답변 메시지 ID로 원래 질문을 찾는다. 상담이 열리지 않은 답변은 결과에 없다. */
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
                JOIN LATERAL (
                    SELECT r.origin_message_id
                    FROM consult_requests r
                    JOIN chat_messages o ON o.message_id = r.origin_message_id
                    WHERE r.session_id = answer.session_id AND o.sequence_no < answer.sequence_no
                    ORDER BY o.sequence_no DESC
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
