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

    public int updateStage(long executionId, String stage, String json, boolean append) {
        // 같은 실행의 병렬 기록이 다른 단계나 배열 항목을 덮어쓰지 않도록 한 UPDATE로 갱신한다.
        String expression = append
                ? "jsonb_build_object(?, COALESCE(pipeline_trace -> ?, '[]'::jsonb)"
                        + " || jsonb_build_array(?::jsonb))"
                : "jsonb_build_object(?, ?::jsonb)";
        String sql = "UPDATE chat_executions SET pipeline_trace="
                + "COALESCE(pipeline_trace,'{}'::jsonb) || " + expression
                + " WHERE execution_id=?";
        return append
                ? jdbc.update(sql, stage, stage, json, executionId)
                : jdbc.update(sql, stage, json, executionId);
    }

    public Optional<Map<String, Object>> findExecution(long sessionId, long executionId) {
        var rows = jdbc.queryForList("""
                SELECT e.execution_id AS "executionId", e.session_id AS "sessionId",
                       e.input_message_id AS "inputMessageId", e.output_message_id AS "outputMessageId",
                       e.status, e.error_code AS "errorCode",
                       i.content AS "originalUserMessage", o.content AS "finalAnswer",
                       o.status AS "answerStatus", o.answer_basis AS "answerBasis",
                       e.pipeline_trace::text AS trace
                FROM chat_executions e
                JOIN chat_messages i ON i.message_id=e.input_message_id
                LEFT JOIN chat_messages o ON o.message_id=e.output_message_id
                WHERE e.execution_id=? AND e.session_id=?
                """, executionId, sessionId);
        return rows.stream().findFirst();
    }

    public List<Map<String, Object>> findRouting(long messageId) {
        return jdbc.queryForList("""
                SELECT intent, refined_query AS "refinedQuery", confidence, method
                FROM query_routings WHERE message_id=?
                """, messageId);
    }

    public List<Map<String, Object>> findModelAttempts(long executionId) {
        return jdbc.queryForList("""
                SELECT generation_id AS "generationId", task_type AS "taskType", attempt, model,
                       prompt_version AS "promptVersion", context_count AS "contextCount",
                       status, first_token_ms AS "firstTokenMs", total_ms AS "totalMs",
                       request_options::text AS configuration
                FROM llm_generations WHERE execution_id=? ORDER BY generation_id
                """, executionId);
    }
}
