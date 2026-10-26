package com.telme.chat.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ChatExecutionTraceRepository {
    private final JdbcTemplate jdbc;

    public static final String TIMELINE = "timeline";

    // 단계 기록마다 timeline에 {stage, at} 한 줄 추가
    private static final String TIMELINE_ENTRY = " || jsonb_build_object('" + TIMELINE + "',"
            + " COALESCE(pipeline_trace -> '" + TIMELINE + "', '[]'::jsonb)"
            + " || jsonb_build_array(jsonb_build_object('stage', ?::text, 'at', to_jsonb(clock_timestamp()))))";

    public int updateStage(long executionId, String stage, String json, boolean append) {
        // 같은 실행의 병렬 기록이 다른 단계나 배열 항목을 덮어쓰지 않도록 한 UPDATE로 갱신
        String expression = append
                ? "jsonb_build_object(?, COALESCE(pipeline_trace -> ?, '[]'::jsonb)"
                        + " || jsonb_build_array(?::jsonb))"
                : "jsonb_build_object(?, ?::jsonb)";
        String sql = "UPDATE chat_executions SET pipeline_trace="
                + "COALESCE(pipeline_trace,'{}'::jsonb) || " + expression + TIMELINE_ENTRY
                + " WHERE execution_id=?";
        return append
                ? jdbc.update(sql, stage, stage, json, stage, executionId)
                : jdbc.update(sql, stage, json, stage, executionId);
    }

    private static final String EXECUTION = """
            SELECT e.execution_id AS "executionId", e.session_id AS "sessionId",
                   e.input_message_id AS "inputMessageId", e.output_message_id AS "outputMessageId",
                   e.status, e.error_code AS "errorCode",
                   i.content AS "originalUserMessage", o.content AS "finalAnswer",
                   o.status AS "answerStatus", o.answer_basis AS "answerBasis",
                   e.pipeline_trace::text AS trace
            FROM chat_executions e
            JOIN chat_messages i ON i.message_id=e.input_message_id
            LEFT JOIN chat_messages o ON o.message_id=e.output_message_id
            WHERE e.execution_id=?
            """;

    public Optional<Map<String, Object>> findExecution(long sessionId, long executionId) {
        var rows = jdbc.queryForList(EXECUTION + " AND e.session_id=?", executionId, sessionId);
        return rows.stream().findFirst();
    }

    // 관리자용 실행 조회(소유자 조건 없음)
    public Optional<Map<String, Object>> findExecutionForAdmin(long executionId) {
        return jdbc.queryForList(EXECUTION, executionId).stream().findFirst();
    }

    public List<Map<String, Object>> findRouting(long messageId) {
        return jdbc.queryForList("""
                SELECT intent, refined_query AS "refinedQuery", confidence, method
                FROM query_routings WHERE message_id=?
                """, messageId);
    }

    public List<Map<String, Object>> findModelAttempts(long executionId) {
        return findModelAttempts(executionId, "");
    }

    // 관리자용 모델 호출 조회(호출 시각 포함)
    public List<Map<String, Object>> findModelAttemptsWithTime(long executionId) {
        return findModelAttempts(executionId, ", created_at AS \"createdAt\"");
    }

    private List<Map<String, Object>> findModelAttempts(long executionId, String extraColumns) {
        return jdbc.queryForList("""
                SELECT generation_id AS "generationId", task_type AS "taskType", attempt, model,
                       prompt_version AS "promptVersion", context_count AS "contextCount",
                       status, first_token_ms AS "firstTokenMs", total_ms AS "totalMs",
                       request_options::text AS configuration%s
                FROM llm_generations WHERE execution_id=? ORDER BY generation_id
                """.formatted(extraColumns), executionId);
    }
}
