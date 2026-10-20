package com.telme.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.config.ChatEndSummaryProperties;
import com.telme.chat.converter.ChatContextFormatter;
import com.telme.chat.guard.ChatInputInspector;
import com.telme.chat.repository.ChatInputMaskRepository;
import com.telme.chat.safety.OutputMaskContext;
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
        List<OutputMaskContext.Source> maskSources = new ArrayList<>();
        List<ChatEndSummaryData.Message> messages = jdbc.query("""
                SELECT m.message_id, m.sequence_no, m.reply_to_id, m.role, m.message_type,
                    m.content, m.store_results::text, m.answer_basis, e.status AS execution_status
                FROM chat_messages m LEFT JOIN chat_executions e ON e.input_message_id=m.message_id
                WHERE m.session_id=? AND m.status='COMPLETED' AND m.message_type NOT IN ('ERROR','BLOCKED')
                ORDER BY m.sequence_no LIMIT ?
                """, (rs, n) -> new ChatEndSummaryData.Message(rs.getLong("message_id"),
                        rs.getInt("sequence_no"), (Long) rs.getObject("reply_to_id"), rs.getString("role"),
                        rs.getString("message_type"), safeContent(rs.getString("content"),
                                rs.getString("store_results"), maskSources), rs.getString("answer_basis"),
                        rs.getString("execution_status")), sessionId, properties.maxMessages() + 1);
        if (messages.size() > properties.maxMessages()) {
            throw new IllegalArgumentException("INPUT_LIMIT");
        }
        var requests = jdbc.query("""
                SELECT consult_request_id, origin_message_id, subquery_order, query_text, status,
                    answer_outcome, answer_text, answer_message_id, display_question, intent
                FROM consult_requests WHERE session_id=? ORDER BY origin_message_id, subquery_order
                """, (rs, n) -> new ChatEndSummaryData.Request(rs.getLong("origin_message_id"),
                        rs.getInt("subquery_order"), safe(rs.getString("query_text"), maskSources),
                        rs.getString("status"),
                        List.of(), rs.getLong("consult_request_id"), rs.getString("answer_outcome"),
                        rs.getString("answer_text") == null ? null : safe(rs.getString("answer_text"), maskSources),
                        (Long) rs.getObject("answer_message_id"),
                        rs.getString("display_question") == null ? null
                                : safe(rs.getString("display_question"), maskSources), rs.getString("intent")),
                sessionId);
        boolean recorded = requests.stream().anyMatch(request -> request.outcome() != null);
        Set<Long> social = new HashSet<>();
        for (var message : messages) {
            if (!message.role().equals("USER")) {
                continue;
            }
            var replies = messages.stream().filter(reply -> Long.valueOf(message.id()).equals(reply.replyTo()))
                    .toList();
            // 감사·작별 인사만 있는 발화는 실시간 경로가 후속 질문으로 접수했어도 문의가 아니다.
            // 그 뒤 답이 다른 하위 질문의 결과로 기록됐으면(조건 답으로 받아 앞 문의를 답한 경우) 그 답은 남긴다.
            // 실시간 경로가 이 발화로 접수한 요청에 실제 안내를 했으면("ㅇㅋ 땡큐"에 앞 질문의 안내가 나간 경우) 빼지 않는다
            boolean guided = requests.stream().anyMatch(request -> request.originMessageId() == message.id()
                    && ("ANSWERED".equals(request.outcome()) || "STORES".equals(request.outcome())));
            if (!guided && ChatEndSummaryClosingRemarks.isClosingRemark(message.content())) {
                social.add(message.id());
                var loaded = requests;
                replies.stream().filter(reply -> loaded.stream().noneMatch(request ->
                                Long.valueOf(reply.id()).equals(request.answerMessageId())
                                        && request.originMessageId() != message.id()))
                        .forEach(reply -> social.add(reply.id()));
                continue;
            }
            if (requests.stream().anyMatch(request -> request.originMessageId() == message.id())) {
                continue;
            }
            // 기본 대화(인사·감사·정체 질문 등)는 서버 고정 문구로만 답한다. 질문이 섞이면 고정 문구가 아닌 답이 나간다
            boolean greeting = (ChatContextFormatter.isSocial(message.content()) || !replies.isEmpty())
                    && replies.stream().allMatch(reply -> isFixedSocial(reply.content()));
            // 상담 요청이 하나도 없는 상담에서 범위 밖 안내만 받은 발화(잡담·인사)는 요약하지 않는다. 결과를 기록한
            // 상담에서는 입력에 남겨 정정 맥락("아 5일 아니고 10일이요"가 범위 밖으로 처리된 경우)을 모델이 보게 하되,
            // 남은 사항·주제·안내로는 쓰지 않는다(recordedPending, topicSources, recordedGuidanceQuotes). 범위 밖 답을
            // 받은 통신 후속 질문도 있지만, 남은 사항 후보로 넣으면 모델이 잡담(날씨·로또·점심)에도 주제를 붙인다(13차 평가)
            boolean outOfScope = requests.isEmpty() && !replies.isEmpty()
                    && replies.stream().allMatch(reply -> "OUT_OF_SCOPE".equals(reply.basis()));
            if (greeting || outOfScope) {
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
                """, (rs, n) -> Map.entry(rs.getLong("consult_request_id"),
                        safe(rs.getString("question"), maskSources)),
                sessionId).stream().collect(Collectors.groupingBy(Map.Entry::getKey,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
        requests = requests.stream().map(request -> request.withPendingQuestions(
                waitingQuestions.getOrDefault(request.requestId(), List.of()))).toList();
        Set<Long> pending = new HashSet<>();
        requests.stream().filter(request -> Set.of("PENDING", "WAITING_CONDITION").contains(request.status()))
                .forEach(request -> pending.add(request.originMessageId()));
        for (var message : messages) {
            if (Set.of("FAILED", "CANCELLED").contains(message.executionStatus() == null
                    ? "" : message.executionStatus())) {
                pending.add(message.id());
            }
            if (!recorded && Set.of("NO_EVIDENCE", "OUT_OF_SCOPE").contains(message.basis() == null ? ""
                    : message.basis()) && message.replyTo() != null && ids.contains(message.replyTo())) {
                pending.add(message.replyTo());
            }
        }
        if (recorded) {
            pending.addAll(recordedPending(messages, requests));
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
                        safe(rs.getString("condition_value"), maskSources), rs.getString("status"),
                        rs.getString("source"),
                        (Long) rs.getObject("answered_message_id"), (Integer) rs.getObject("sequence_no")),
                sessionId).stream().filter(condition -> ids.contains(condition.originMessageId())).toList();
        maskSources.addAll(inputMasks.read(sessionId, through).sources());
        return new ChatEndSummaryData.Input(sessionId, through,
                executions.isEmpty() ? null : executions.getFirst(), messages, requests, pending, conditions,
                new OutputMaskContext(maskSources));
    }

    private String safe(String text, List<OutputMaskContext.Source> maskSources) {
        if (text == null) {
            return "";
        }
        var inspection = inspector.inspect(text);
        if (!inspection.maskSpans().isEmpty()) {
            maskSources.add(OutputMaskContext.Source.from(inspection));
        }
        return inspection.content();
    }

    private String safeContent(String text, String stores, List<OutputMaskContext.Source> maskSources) {
        String result = safe(text, maskSources);
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
                        labels.add(safe(name + " " + address, maskSources));
                    }
                }
            }
            return result + (labels.isEmpty() ? "" : "\n안내한 매장: " + String.join(", ", labels));
        } catch (java.io.IOException exception) {
            throw new IllegalArgumentException("INVALID_INPUT");
        }
    }

    /**
     * 실시간 경로가 기록한 결과로 정한 남은 문의.
     * - 답하지 못했거나(NO_EVIDENCE) 나눠 보내 달라는 안내로 처리하지 않은(SPLIT_REFUSED) 하위 질문
     * - 되묻다가 고객이 다른 질문으로 넘어가 취소된 하위 질문(CANCELLED·ASKED)
     * - 상담 요청 없이 받은 안내 문구(주제 확인, 나눠 보내기, 질문 개수 초과). 이 답들은 상담 요청을 만들지 않는
     *   직접 답변(근거 표시 없음)이고 어느 하위 질문의 결과로도 기록되지 않는다. 범위 밖 안내는 위에서 뺐다
     */
    private static Set<Long> recordedPending(List<ChatEndSummaryData.Message> messages,
            List<ChatEndSummaryData.Request> requests) {
        Set<Long> pending = new HashSet<>();
        for (var request : requests) {
            boolean unanswered = Set.of("NO_EVIDENCE", "SPLIT_REFUSED").contains(String.valueOf(request.outcome()));
            boolean abandoned = "CANCELLED".equals(request.status()) && "ASKED".equals(request.outcome());
            if (unanswered || abandoned) {
                pending.add(request.originMessageId());
            }
        }
        Set<Long> recordedMessages = requests.stream().map(ChatEndSummaryData.Request::answerMessageId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Set<Long> origins = requests.stream().map(ChatEndSummaryData.Request::originMessageId)
                .collect(Collectors.toSet());
        for (var message : messages) {
            if (message.role().equals("ASSISTANT") && message.type().equals("ANSWER") && message.basis() == null
                    && message.replyTo() != null && !recordedMessages.contains(message.id())
                    && !origins.contains(message.replyTo())
                    && messages.stream().anyMatch(user -> user.id() == message.replyTo())) {
                pending.add(message.replyTo());
            }
        }
        // 되묻는 중에 고객이 조건 대신 새 질문을 하면 실시간 경로가 조건 답으로 받아 같은 질문을 다시 묻는다
        // ("유심 비번 까먹었어요"에 "답변을 잘 이해하지 못했어요"). 새로 묻는 되묻기는 하위 질문 결과(ASKED)로
        // 기록되지만 다시 묻기는 기록되지 않는다. 상담 요청 없는 발화에 기록되지 않은 되묻기만 돌아왔으면 남은 문의
        // 후보로 둔다. 조건 답을 못 알아들은 경우와 섞이므로 모델이 주제를 붙였을 때만 적는다(생성기)
        for (var user : messages) {
            if (!user.role().equals("USER") || origins.contains(user.id())) {
                continue;
            }
            var replies = messages.stream().filter(reply -> Long.valueOf(user.id()).equals(reply.replyTo())).toList();
            if (!replies.isEmpty() && replies.stream().allMatch(reply -> reply.type().equals("CLARIFICATION")
                    && !recordedMessages.contains(reply.id()))) {
                pending.add(user.id());
            }
        }
        return pending;
    }

    // 기본 대화 고정 응답(페르소나). 이전 문구는 페르소나 도입 전에 저장된 대화를 위해 남긴다
    private boolean isFixedSocial(String value) {
        return BasicConversationResponder.isFixedResponse(value) || Set.of("안녕하세요. 무엇을 도와드릴까요?",
                "천만에요. 더 궁금한 점이 있으면 말씀해 주세요.",
                "이용해 주셔서 감사합니다. 궁금한 점이 생기면 다시 말씀해 주세요.").contains(value);
    }
}
