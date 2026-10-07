package com.telme.consult.repository;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** 되물어서 받은 조건에 그때 물은 질문 문구를 붙인다. 답변 모델은 "예"만으로는 무엇에 대한 답인지 알 수 없다. */
public final class AskedConditionQuestionFinder implements AskedQuestions {
    private final JdbcTemplate jdbc;

    public AskedConditionQuestionFinder(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    public Map<String, String> of(long consultRequestId) {
        if (consultRequestId <= 0) {
            return Map.of();
        }
        Map<String, String> questions = new HashMap<>();
        jdbc.query(
                """
                SELECT c.condition_key, q.content
                FROM consult_conditions c
                JOIN chat_messages q ON q.message_id=c.asked_message_id
                WHERE c.consult_request_id=? AND q.message_type='CLARIFICATION'
                """,
                rs -> {
                    String question = rs.getString(2);
                    if (question != null && !question.isBlank()) {
                        questions.put(rs.getString(1), question.strip());
                    }
                },
                consultRequestId);
        return Map.copyOf(questions);
    }
}
