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

    // 요금제 추천 답변(MemberRecommendationAnswerer)에서 추천한 요금제를 알려주는 문장을 찾는 문구. 답변 문구와 같아야 한다.
    // 가입 정보가 없을 때("…요금제를 추천해요.")와 사용량이 있는 회원에게 나가는 세 가지("…으로 바꾸면 … 아낄 수 있어요.",
    // "…을 추천해요. 월 …이 더 들어요.", "…그대로 쓰시는 걸 추천해요.")가 있다
    public static final String RECOMMENDATION_MARK = "요금제를 추천해요.";
    public static final String CHEAPER_MARK = "으로 바꾸면 사용량을 채우면서";
    public static final String CHEAPER_END = "아낄 수 있어요.";
    public static final String UPGRADE_MARK = "을 추천해요. 월 ";
    public static final String UPGRADE_END = "을 추천해요.";
    public static final String KEEP_MARK = "그대로 쓰시는 걸 추천해요.";
    // 사용량 문장의 끝과 현재 요금제가 부족하다는 문장의 끝. 이 뒤에 추천한 요금제가 나온다
    public static final String USE_END = "건이에요. ";
    public static final String SHORTAGE_END = "부족해요. ";

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

    // 같은 채팅방에서 가장 최근에 한 요금제 추천 답변의 본문(어느 형태든 가장 최근 한 건). 신청·변경이 "그 요금제"가 무엇인지 찾을 때 쓴다.
    // 답변 처리에는 질문 복원이 고른 이전 고객 발언만 넘어오고 어시스턴트 답변은 오지 않아 저장된 메시지를 직접 읽는다
    public Optional<String> latestRecommendation(long sessionId) {
        return jdbc.query("""
                SELECT content FROM chat_messages
                WHERE session_id = ? AND role = 'ASSISTANT' AND message_type = 'ANSWER'
                  AND (content LIKE ? OR content LIKE ? OR content LIKE ? OR content LIKE ?)
                ORDER BY sequence_no DESC LIMIT 1
                """, (rs, row) -> rs.getString(1), sessionId, "%" + RECOMMENDATION_MARK + "%", "%" + CHEAPER_MARK + "%",
                "%" + UPGRADE_MARK + "%", "%" + KEEP_MARK + "%")
                .stream().findFirst();
    }
}
