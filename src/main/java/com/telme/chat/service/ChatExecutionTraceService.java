package com.telme.chat.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.telme.chat.exception.ChatErrorCode;
import com.telme.global.common.exception.GeneralException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatExecutionTraceService implements ExecutionTrace {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final ChatSessionService sessions;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void stage(Long executionId, String stage, Object value) {
        write(executionId, stage, value, false);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void append(Long executionId, String stage, Object value) {
        write(executionId, stage, value, true);
    }

    private void write(Long executionId, String stage, Object value, boolean append) {
        if (executionId == null) return;
        try {
            String json = mapper.writeValueAsString(value);
            // Atomic row-local JSON updates preserve different stages and parallel append operations.
            String expression = append
                    ? "jsonb_build_object(?, COALESCE(pipeline_trace -> ?, '[]'::jsonb)"
                            + " || jsonb_build_array(?::jsonb))"
                    : "jsonb_build_object(?, ?::jsonb)";
            String sql = "UPDATE chat_executions SET pipeline_trace="
                    + "COALESCE(pipeline_trace,'{}'::jsonb) || " + expression
                    + " WHERE execution_id=?";
            int changed = append
                    ? jdbc.update(sql, stage, stage, json, executionId)
                    : jdbc.update(sql, stage, json, executionId);
            if (changed != 1) log.warn("Execution trace target unavailable executionId={} stage={}",
                    executionId, stage);
        } catch (RuntimeException | JsonProcessingException failure) {
            // Diagnostic storage failure must not change generation, Guard, persistence or SSE.
            // Do not log the values or exception text, which can contain user content.
            log.warn("Execution trace write failed executionId={} stage={} type={}",
                    executionId, stage, failure.getClass().getSimpleName());
        }
    }

    @Transactional(readOnly = true)
    public JsonNode get(ChatActor actor, long sessionId, long executionId) {
        // Same member/guest ownership check as the SSE subscription, before reading any trace.
        var execution = sessions.getExecution(actor, executionId);
        if (execution.sessionId() != sessionId) {
            throw new GeneralException(ChatErrorCode.EXECUTION_NOT_FOUND);
        }
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
        if (rows.isEmpty()) throw new GeneralException(ChatErrorCode.EXECUTION_NOT_FOUND);
        Map<String, Object> row = rows.getFirst();
        String trace = (String) row.remove("trace");
        ObjectNode result = mapper.valueToTree(row);
        result.put("traceRecorded", trace != null);
        try {
            JsonNode steps = trace == null ? mapper.nullNode() : mapper.readTree(trace);
            if (steps.has("generationInputs") && steps.path("generationInputs").size() == 1) {
                ((ObjectNode) steps).set("generationInput", steps.path("generationInputs").get(0));
            }
            result.set("steps", steps);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Invalid stored execution trace", failure);
        }
        result.set("routing", mapper.valueToTree(jdbc.queryForList("""
                SELECT intent, refined_query AS "refinedQuery", confidence, method
                FROM query_routings WHERE message_id=?
                """, row.get("inputMessageId"))));
        var attempts = jdbc.queryForList("""
                SELECT generation_id AS "generationId", task_type AS "taskType", attempt, model,
                       prompt_version AS "promptVersion", context_count AS "contextCount",
                       status, first_token_ms AS "firstTokenMs", total_ms AS "totalMs",
                       request_options::text AS configuration
                FROM llm_generations WHERE execution_id=? ORDER BY generation_id
                """, executionId);
        for (var attempt : attempts) {
            String options = (String) attempt.get("configuration");
            try {
                attempt.put("configuration", options == null ? null : mapper.readTree(options));
            } catch (JsonProcessingException failure) {
                throw new IllegalStateException("Invalid stored model configuration", failure);
            }
        }
        result.set("modelAttempts", mapper.valueToTree(attempts));
        return result;
    }
}
