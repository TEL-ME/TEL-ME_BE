package com.telme.consult.repository;

import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

// 같은 채팅방에서 앞서 요금제 추천 질문에 한 나이대·데이터 사용량 답. 키마다 가장 최근 값
@Component
@RequiredArgsConstructor
public class SessionRecommendationAnswers {

    private final JdbcTemplate jdbc;

    public Map<String, String> of(long sessionId) {
        Map<String, String> answers = new HashMap<>();
        jdbc.query("""
                SELECT DISTINCT ON (c.condition_key) c.condition_key, c.condition_value
                FROM consult_conditions c
                JOIN consult_requests r ON r.consult_request_id = c.consult_request_id
                WHERE r.session_id = ? AND c.condition_key IN ('age_group', 'heavy_data')
                  AND c.status = 'FILLED' AND c.condition_value IS NOT NULL
                ORDER BY c.condition_key, c.updated_at DESC, c.condition_id DESC
                """, rows -> {
            answers.put(rows.getString(1), rows.getString(2));
        }, sessionId);
        return answers;
    }
}
