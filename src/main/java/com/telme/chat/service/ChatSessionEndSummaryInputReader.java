package com.telme.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.config.ChatEndSummaryProperties;
import com.telme.chat.converter.ChatContextFormatter;
import com.telme.chat.guard.ChatInputInspector;
import com.telme.chat.repository.ChatInputMaskRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class ChatSessionEndSummaryInputReader {
    private final JdbcTemplate jdbc;
    private final ChatInputInspector inspector;
    private final ObjectMapper mapper;
    private final ChatEndSummaryProperties properties;
    private final ChatInputMaskRepository inputMasks;

    @Transactional(readOnly = true)
    ChatEndSummaryData.Input read(long sessionId) {
        List<ChatEndSummaryData.Message> messages = jdbc.query("""
                SELECT m.message_id, m.sequence_no, m.reply_to_id, m.role, m.message_type,
                    m.content, m.store_results::text, m.answer_basis, e.status AS execution_status
                FROM chat_messages m LEFT JOIN chat_executions e ON e.input_message_id=m.message_id
                WHERE m.session_id=? AND m.status='COMPLETED' AND m.message_type NOT IN ('ERROR','BLOCKED')
                ORDER BY m.sequence_no LIMIT ?
                """, (rs, n) -> new ChatEndSummaryData.Message(rs.getLong("message_id"),
                        rs.getInt("sequence_no"), (Long) rs.getObject("reply_to_id"), rs.getString("role"),
                        rs.getString("message_type"), safeContent(rs.getString("content"),
                                rs.getString("store_results")), rs.getString("answer_basis"),
                        rs.getString("execution_status")), sessionId, properties.maxMessages() + 1);
        if (messages.size() > properties.maxMessages()) {
            throw new IllegalArgumentException("INPUT_LIMIT");
        }
        var requests = jdbc.query("""
                SELECT consult_request_id, origin_message_id, subquery_order, query_text, status
                FROM consult_requests WHERE session_id=? ORDER BY origin_message_id, subquery_order
                """, (rs, n) -> new ChatEndSummaryData.Request(rs.getLong("origin_message_id"),
                        rs.getInt("subquery_order"), safe(rs.getString("query_text")), rs.getString("status"),
                        List.of(), rs.getLong("consult_request_id")),
                sessionId);
        Set<Long> social = new HashSet<>();
        for (var message : messages) {
            if (!message.role().equals("USER") || !ChatContextFormatter.isSocial(message.content())
                    || requests.stream().anyMatch(request -> request.originMessageId() == message.id())) {
                continue;
            }
            var replies = messages.stream().filter(reply -> Long.valueOf(message.id()).equals(reply.replyTo()))
                    .toList();
            if (replies.stream().allMatch(reply -> isFixedSocial(reply.content()))) {
                social.add(message.id());
                replies.forEach(reply -> social.add(reply.id()));
            }
        }
        messages = messages.stream().filter(message -> !social.contains(message.id())).toList();
        Set<Long> ids = new HashSet<>();
        messages.forEach(message -> ids.add(message.id()));
        requests = requests.stream().filter(request -> ids.contains(request.originMessageId())).toList();
        var waitingQuestions = jdbc.query("""
                SELECT DISTINCT r.consult_request_id, coalesce(p.value->>'question', q.content) AS question
                FROM consult_requests r JOIN consult_conditions c USING (consult_request_id)
                LEFT JOIN chat_messages q ON q.message_id=c.asked_message_id
                LEFT JOIN LATERAL jsonb_array_elements(
                    coalesce(r.clarification_plan->'conditions', '[]'::jsonb)) p(value)
                    ON p.value->>'key'=c.condition_key
                WHERE r.session_id=? AND r.status='WAITING_CONDITION' AND c.status='PENDING'
                    AND coalesce(p.value->>'question', q.content) IS NOT NULL
                ORDER BY r.consult_request_id, question
                """, (rs, n) -> Map.entry(rs.getLong("consult_request_id"), safe(rs.getString("question"))),
                sessionId).stream().collect(Collectors.groupingBy(Map.Entry::getKey,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
        requests = requests.stream().map(request -> new ChatEndSummaryData.Request(request.originMessageId(),
                request.order(), request.query(), request.status(),
                waitingQuestions.getOrDefault(request.requestId(), List.of()), request.requestId())).toList();
        Set<Long> pending = new HashSet<>();
        requests.stream().filter(request -> Set.of("PENDING", "WAITING_CONDITION").contains(request.status()))
                .forEach(request -> pending.add(request.originMessageId()));
        for (var message : messages) {
            if (Set.of("FAILED", "CANCELLED").contains(message.executionStatus() == null
                    ? "" : message.executionStatus())) {
                pending.add(message.id());
            }
            if (Set.of("NO_EVIDENCE", "OUT_OF_SCOPE").contains(message.basis() == null ? "" : message.basis())
                    && message.replyTo() != null && ids.contains(message.replyTo())) {
                pending.add(message.replyTo());
            }
        }
        if (requests.isEmpty() && !messages.isEmpty()) {
            var last = messages.getLast();
            if (last.type().equals("CLARIFICATION") && last.replyTo() != null && ids.contains(last.replyTo())) {
                pending.add(last.replyTo());
            }
        }
        int through = jdbc.queryForObject("""
                SELECT coalesce(max(sequence_no),0) FROM chat_messages WHERE session_id=?
                """, Integer.class, sessionId);
        var executions = jdbc.queryForList("""
                SELECT execution_id FROM chat_executions WHERE session_id=? ORDER BY execution_id DESC LIMIT 1
                """, Long.class, sessionId);
        var conditions = jdbc.query("""
                SELECT r.consult_request_id, r.origin_message_id, c.condition_key, c.condition_value,
                    c.status, c.source, c.answered_message_id, a.sequence_no
                FROM consult_requests r JOIN consult_conditions c USING (consult_request_id)
                LEFT JOIN chat_messages a ON a.message_id=c.answered_message_id
                WHERE r.session_id=? ORDER BY r.consult_request_id, c.condition_key
                """, (rs, n) -> new ChatEndSummaryData.Condition(rs.getLong("consult_request_id"),
                        rs.getLong("origin_message_id"), rs.getString("condition_key"),
                        safe(rs.getString("condition_value")), rs.getString("status"), rs.getString("source"),
                        (Long) rs.getObject("answered_message_id"), (Integer) rs.getObject("sequence_no")),
                sessionId).stream().filter(condition -> ids.contains(condition.originMessageId())).toList();
        return new ChatEndSummaryData.Input(sessionId, through,
                executions.isEmpty() ? null : executions.getFirst(), messages, requests, pending, conditions,
                inputMasks.read(sessionId, through));
    }

    private String safe(String text) {
        return text == null ? "" : inspector.inspect(text).content();
    }

    private String safeContent(String text, String stores) {
        String result = safe(text);
        if (stores == null) {
            return result;
        }
        try {
            var root = mapper.readTree(stores);
            List<String> labels = new ArrayList<>();
            if (root.isArray()) {
                for (var store : root) {
                    String name = store.path("name").asText("");
                    String address = store.path("address").asText("");
                    if (!name.isBlank() || !address.isBlank()) {
                        labels.add(safe(name + " " + address));
                    }
                }
            }
            return result + (labels.isEmpty() ? "" : "\n안내한 매장: " + String.join(", ", labels));
        } catch (java.io.IOException exception) {
            throw new IllegalArgumentException("INVALID_INPUT");
        }
    }

    private boolean isFixedSocial(String value) {
        return Set.of("안녕하세요. 무엇을 도와드릴까요?",
                "천만에요. 더 궁금한 점이 있으면 말씀해 주세요.",
                "이용해 주셔서 감사합니다. 궁금한 점이 생기면 다시 말씀해 주세요.").contains(value);
    }
}
