package com.telme.chat.repository;

import com.telme.chat.dto.req.AdminChatMonitoringStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

// 관리자 모니터링 목록 조회
@Repository
@RequiredArgsConstructor
public class AdminChatMonitoringRepository {
    private final JdbcTemplate jdbc;
    private final AdminChatMonitoringRowMapper rowMapper;

    // 사용자 입력 메시지 기준 + trace는 JSONPath로 요약에 쓸 작은 배열만 추출
    private static final String SELECT = """
            SELECT m.message_id, m.message_type, m.session_id, m.content, m.created_at AS message_created_at,
                   s.user_id, u.name AS user_name, s.guest_id::text AS guest_id,
                   e.execution_id, e.status AS execution_status, e.started_at, e.ended_at, e.error_code,
                   o.message_type AS answer_type, o.answer_basis, r.intent,
                   (SELECT string_agg(DISTINCT a, ',') FROM chat_input_guard_events g
                        CROSS JOIN LATERAL jsonb_array_elements_text(g.actions) a
                     WHERE g.message_id = m.message_id) AS input_actions,
                   t -> 'analysis' ->> 'action' AS analysis_action,
                   (jsonb_path_query_first(t, '$.searchRequests.size()'))::int AS search_request_count,
                   (t -> 'searchRequests' -> -1 ->> 'grayZone')::boolean AS last_search_gray_zone,
                   jsonb_path_query_array(t, '$.searchResults[*].status') AS search_statuses,
                   jsonb_path_query_array(t, '$.searchResults[*].sources[*].faqId') AS source_faq_ids,
                   jsonb_path_query_array(t, '$.refinedQueryChecks[*].searched') AS refined_searched,
                   jsonb_path_query_array(t, '$.grayZoneJudgments[*].judgments[*].kept') AS judgment_kept,
                   jsonb_path_query_array(t, '$.grayZoneJudgments[*].judgments[*].verdict') AS judgment_verdicts,
                   t -> 'planAnswer' IS NOT NULL AS plan_answer,
                   t -> 'comparisonAnswer' IS NOT NULL AS comparison_answer,
                   t -> 'faqCandidateAnswer' IS NOT NULL AS faq_candidate_answer,
                   t -> 'generationInputs' IS NOT NULL AS generation_started,
                   t -> 'guard' ->> 'outcome' AS guard_outcome,
                   t -> 'guard' ->> 'reason' AS guard_reason,
                   t -> 'outputSafety' ->> 'outcome' AS output_safety_outcome,
                   t -> 'finalTransmission' ->> 'status' AS final_transmission_status,
                   t -> 'timeline' -> -1 ->> 'stage' AS last_stage
            FROM chat_messages m
            JOIN chat_sessions s ON s.session_id = m.session_id
            LEFT JOIN users u ON u.user_id = s.user_id
            LEFT JOIN chat_executions e ON e.input_message_id = m.message_id
            LEFT JOIN chat_messages o ON o.message_id = e.output_message_id
            LEFT JOIN query_routings r ON r.message_id = m.message_id
            CROSS JOIN LATERAL (SELECT e.pipeline_trace AS t) trace
            WHERE m.role = 'USER'
            """;

    public List<AdminChatMonitoringRow> findPage(long beforeId, AdminChatMonitoringStatus status, int limit) {
        List<Object> args = new ArrayList<>(List.of(beforeId));
        StringBuilder sql = new StringBuilder(SELECT).append(" AND m.message_id < ?");
        // 상태 필터는 부분 인덱스로 대상 ID 51개부터 고른다(플래너가 메시지 전체를 훑지 않게)
        if (status == AdminChatMonitoringStatus.BLOCKED) {
            sql.append(" AND m.message_id IN (SELECT message_id FROM chat_messages"
                    + " WHERE message_type = 'BLOCKED' AND message_id < ? ORDER BY message_id DESC LIMIT ?)");
            args.addAll(List.of(beforeId, limit));
        } else if (status != null) {
            sql.append(" AND e.status = ? AND m.message_id IN (SELECT input_message_id FROM chat_executions"
                    + " WHERE status = ? AND input_message_id < ? ORDER BY input_message_id DESC LIMIT ?)");
            args.addAll(List.of(status.name(), status.name(), beforeId, limit));
        }
        // PK 커서
        sql.append(" ORDER BY m.message_id DESC LIMIT ?");
        args.add(limit);
        return jdbc.query(sql.toString(), rowMapper, args.toArray());
    }

    public Optional<AdminChatMonitoringRow> findByMessageId(long messageId) {
        return jdbc.query(SELECT + " AND m.message_id = ?", rowMapper, messageId).stream().findFirst();
    }
}
