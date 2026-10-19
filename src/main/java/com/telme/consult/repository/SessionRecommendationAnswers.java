package com.telme.consult.repository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

// 같은 채팅방에서 앞서 요금제 추천 질문에 한 나이대·데이터·통화·문자 사용량 답. 키마다 가장 최근 값
@Component
@RequiredArgsConstructor
public class SessionRecommendationAnswers {

    // 추천 답변(MemberRecommendationAnswerer.recommendation)의 첫 문장 끝
    public static final String RECOMMENDATION_MARK = "요금제를 추천해요.";

    private final JdbcTemplate jdbc;

    public Map<String, String> of(long sessionId) {
        Map<String, String> answers = new HashMap<>();
        jdbc.query("""
                SELECT DISTINCT ON (c.condition_key) c.condition_key, c.condition_value
                FROM consult_conditions c
                JOIN consult_requests r ON r.consult_request_id = c.consult_request_id
                WHERE r.session_id = ? AND c.condition_key IN ('age_group', 'monthly_data_usage', 'monthly_voice_usage', 'monthly_sms_usage')
                  AND c.status = 'FILLED' AND c.condition_value IS NOT NULL
                ORDER BY c.condition_key, c.updated_at DESC, c.condition_id DESC
                """, rows -> {
            answers.put(rows.getString(1), rows.getString(2));
        }, sessionId);
        return answers;
    }

    // 같은 채팅방에서 가장 최근에 한 요금제 추천 답변의 본문. 신청·변경이 "그 요금제"가 무엇인지 찾을 때 쓴다.
    // 답변 처리에는 질문 복원이 고른 이전 고객 발언만 넘어오고 어시스턴트 답변은 오지 않아 저장된 메시지를 직접 읽는다
    public Optional<String> latestRecommendation(long sessionId) {
        return jdbc.query("""
                SELECT content FROM chat_messages
                WHERE session_id = ? AND role = 'ASSISTANT' AND message_type = 'ANSWER'
                  AND content LIKE ?
                ORDER BY sequence_no DESC LIMIT 1
                """, (rs, row) -> rs.getString(1), sessionId, "%" + RECOMMENDATION_MARK + "%")
                .stream().findFirst();
    }
}
