package com.telme.consult.repository;

import com.telme.consult.dto.DialogueDecision;
import com.telme.consult.dto.DialogueDecision.Action;
import com.telme.consult.dto.DialogueInput.Condition;
import com.telme.consult.dto.DialogueInput.ConditionStatus;
import com.telme.consult.exception.ConsultErrorCode;
import com.telme.global.common.exception.GeneralException;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** 상담 상태 저장. 인증·대화 소유권은 호출자가 확인한다. */
public final class JdbcConsultStateStore {
    public record Snapshot(
            long requestId,
            long sessionId,
            int version,
            String status,
            Map<String, Condition> conditions) {
        public Snapshot {
            conditions = Map.copyOf(conditions);
        }
    }

    /** answeredField는 기존 되묻기에 답한 조건 이름이다. */
    public record MessageLinks(
            Long clarificationMessageId, Long answerMessageId, String answeredField) {
        public MessageLinks {
            if ((answerMessageId == null) != (answeredField == null)) {
                throw new IllegalArgumentException("Answer id and field must be paired");
            }
        }

        public static MessageLinks none() {
            return new MessageLinks(null, null, null);
        }
    }

    public static class StateConflict extends GeneralException {
        public StateConflict() {
            super(ConsultErrorCode.STATE_CONFLICT);
        }
    }

    public static class ClarificationAlreadyPending extends RuntimeException {
        private final long messageId;

        public ClarificationAlreadyPending(long messageId) {
            super("A clarification for this field is already pending");
            this.messageId = messageId;
        }

        public long messageId() {
            return messageId;
        }
    }

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public JdbcConsultStateStore(JdbcTemplate jdbc, TransactionTemplate tx) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.tx = Objects.requireNonNull(tx);
    }

    public Snapshot load(long sessionId, long requestId) {
        return tx.execute(status -> read(sessionId, requestId));
    }

    private Snapshot read(long sessionId, long requestId) {
        var requests =
                jdbc.query(
                        "SELECT version,status FROM consult_requests WHERE consult_request_id=? AND"
                                + " session_id=? FOR UPDATE",
                        (rs, n) ->
                                new Snapshot(
                                        requestId,
                                        sessionId,
                                        rs.getInt("version"),
                                        rs.getString("status"),
                                        Map.of()),
                        requestId,
                        sessionId);
        if (requests.isEmpty()) {
            throw new GeneralException(ConsultErrorCode.REQUEST_NOT_FOUND);
        }
        var r = requests.getFirst();
        Map<String, Condition> conditions = new HashMap<>();
        jdbc.query(
                "SELECT condition_key,condition_value,status FROM consult_conditions WHERE"
                        + " consult_request_id=?",
                rs -> {
                    conditions.put(
                            rs.getString("condition_key"),
                            new Condition(
                                    ConditionStatus.valueOf(rs.getString("status")),
                                    rs.getString("condition_value")));
                },
                requestId);
        return new Snapshot(requestId, sessionId, r.version(), r.status(), conditions);
    }

    public Optional<Long> findPendingClarificationMessageId(
            long sessionId, long requestId, String field) {
        var messages =
                jdbc.queryForList(
                        "SELECT c.asked_message_id FROM consult_conditions c JOIN consult_requests"
                            + " r ON r.consult_request_id=c.consult_request_id WHERE r.session_id=?"
                            + " AND r.consult_request_id=? AND r.status='WAITING_CONDITION' AND"
                            + " c.condition_key=? AND c.status='PENDING' AND c.asked_message_id IS"
                            + " NOT NULL",
                        Long.class,
                        sessionId,
                        requestId,
                        field);
        return messages.stream().findFirst();
    }

    public Snapshot save(
            long sessionId, int expectedVersion, DialogueDecision decision, MessageLinks links) {
        return saveInternal(sessionId, expectedVersion, decision, links, null);
    }

    public Snapshot saveWhileWaiting(
            long sessionId, int expectedVersion, DialogueDecision decision, long pendingMessageId) {
        return saveInternal(
                sessionId, expectedVersion, decision, MessageLinks.none(), pendingMessageId);
    }

    private Snapshot saveInternal(
            long sessionId,
            int expectedVersion,
            DialogueDecision decision,
            MessageLinks links,
            Long pendingMessageId) {
        Objects.requireNonNull(decision);
        Objects.requireNonNull(links);
        return tx.execute(
                txStatus -> {
                    var old = read(sessionId, decision.consultRequestId());
                    requireOpenVersion(old, expectedVersion);
                    validatePreservedConditions(old, decision);
                    boolean keepsWaiting = pendingMessageId != null;
                    if (keepsWaiting) {
                        validateWaiting(old, decision, pendingMessageId, sessionId);
                    }
                    boolean asks = decision.action() == Action.ASK && !keepsWaiting;
                    if (asks != (links.clarificationMessageId() != null)) {
                        throw new IllegalArgumentException(
                                "ASK needs a saved clarification message");
                    }
                    var conditions = new HashMap<>(decision.conditions());
                    if (asks) {
                        validateClarification(old, decision, links, conditions, sessionId);
                    }
                    if (links.answerMessageId() != null) {
                        validateAnswer(old, links, conditions, sessionId);
                    }
                    saveConditions(old, decision, links, conditions, asks);
                    // 최종 답변이 저장되기 전에는 상담을 완료하지 않는다.
                    jdbc.update(
                            "UPDATE consult_requests SET"
                                    + " status=?,version=version+1,updated_at=now() WHERE"
                                    + " consult_request_id=?",
                            asks || keepsWaiting ? "WAITING_CONDITION" : "PENDING",
                            old.requestId());
                    return read(sessionId, old.requestId());
                });
    }

    private void validatePreservedConditions(Snapshot old, DialogueDecision decision) {
        if (!decision.conditions().keySet().containsAll(old.conditions().keySet())) {
            throw new IllegalArgumentException("Existing conditions must be preserved");
        }
    }

    private void validateWaiting(
            Snapshot old, DialogueDecision decision, long pendingMessageId, long sessionId) {
        var condition = decision.conditions().get(decision.waitingField());
        if (decision.action() != Action.ASK
                || !"WAITING_CONDITION".equals(old.status())
                || condition == null
                || condition.status() != ConditionStatus.PENDING) {
            throw new IllegalArgumentException("기존 질문 대기 상태를 유지해야 합니다.");
        }
        var pending =
                findPendingClarificationMessageId(
                        sessionId, old.requestId(), decision.waitingField());
        if (pending.isEmpty() || !pending.get().equals(pendingMessageId)) {
            throw new StateConflict();
        }
    }

    private void validateClarification(
            Snapshot old,
            DialogueDecision decision,
            MessageLinks links,
            Map<String, Condition> conditions,
            long sessionId) {
        if (decision.waitingField() == null || decision.waitingField().isBlank()) {
            throw new IllegalArgumentException("Missing waiting field");
        }
        var existing = conditions.get(decision.waitingField());
        if (existing != null && existing.status() != ConditionStatus.PENDING) {
            throw new IllegalArgumentException("Cannot ask a resolved condition");
        }
        var pending =
                jdbc.queryForList(
                        "SELECT asked_message_id FROM consult_conditions WHERE"
                                + " consult_request_id=? AND condition_key=? AND"
                                + " status='PENDING' AND asked_message_id IS NOT NULL",
                        Long.class,
                        old.requestId(),
                        decision.waitingField());
        if (!pending.isEmpty()) {
            throw new ClarificationAlreadyPending(pending.getFirst());
        }
        conditions.put(decision.waitingField(), Condition.pending());
        checkMessage(links.clarificationMessageId(), sessionId, "ASSISTANT", "CLARIFICATION");
        int later =
                jdbc.queryForObject(
                        "SELECT count(*) FROM chat_messages q JOIN consult_requests"
                                + " r ON r.consult_request_id=? JOIN chat_messages"
                                + " origin ON origin.message_id=r.origin_message_id"
                                + " WHERE q.message_id=? AND q.session_id=r.session_id"
                                + " AND origin.session_id=r.session_id AND"
                                + " q.sequence_no>origin.sequence_no",
                        Integer.class,
                        old.requestId(),
                        links.clarificationMessageId());
        if (later != 1) {
            throw new IllegalArgumentException("Clarification must follow its origin question");
        }
    }

    private void validateAnswer(
            Snapshot old, MessageLinks links, Map<String, Condition> conditions, long sessionId) {
        checkMessage(links.answerMessageId(), sessionId, "USER", "QUESTION");
        var value = conditions.get(links.answeredField());
        if (value == null || value.status() == ConditionStatus.PENDING) {
            throw new IllegalArgumentException("Answer must fill or decline its condition");
        }
        var asked =
                jdbc.queryForList(
                        "SELECT asked_message_id FROM consult_conditions WHERE"
                                + " consult_request_id=? AND condition_key=? AND"
                                + " status='PENDING' AND asked_message_id IS NOT NULL",
                        Long.class,
                        old.requestId(),
                        links.answeredField());
        if (asked.isEmpty()) {
            throw new IllegalArgumentException("No pending clarification for field");
        }
        int later =
                jdbc.queryForObject(
                        "SELECT count(*) FROM chat_messages a JOIN chat_messages q"
                                + " ON q.message_id=? WHERE a.message_id=? AND"
                                + " a.session_id=q.session_id AND"
                                + " a.sequence_no>q.sequence_no",
                        Integer.class,
                        asked.getFirst(),
                        links.answerMessageId());
        if (later != 1) {
            throw new IllegalArgumentException("Followup must be later than its clarification");
        }
    }

    private void saveConditions(
            Snapshot old,
            DialogueDecision decision,
            MessageLinks links,
            Map<String, Condition> conditions,
            boolean asks) {
        for (var entry : conditions.entrySet()) {
            String key = entry.getKey();
            if (key.isBlank() || key.length() > 50) {
                throw new IllegalArgumentException("Invalid condition key");
            }
            var c = entry.getValue();
            boolean isAsked = asks && key.equals(decision.waitingField());
            boolean isAnswered = key.equals(links.answeredField());
            jdbc.update(
                    "INSERT INTO consult_conditions"
                        + " (consult_request_id,condition_key,condition_value,status,source) VALUES"
                        + " (?,?,?,?,?) ON CONFLICT (consult_request_id,condition_key) DO UPDATE"
                        + " SET condition_value=EXCLUDED.condition_value,status=EXCLUDED.status,updated_at=now()",
                    old.requestId(),
                    key,
                    c.value(),
                    c.status().name(),
                    isAsked || isAnswered ? "ASKED" : "EXTRACTED");
            if (!isAsked && !isAnswered && !Objects.equals(c, old.conditions().get(key))) {
                // 직접 정정한 값에는 이전 답변 메시지를 연결하지 않는다.
                jdbc.update(
                        "UPDATE consult_conditions SET"
                            + " source='EXTRACTED',asked_message_id=NULL,answered_message_id=NULL"
                            + " WHERE consult_request_id=? AND condition_key=?",
                        old.requestId(),
                        key);
            }
            if (isAsked) {
                jdbc.update(
                        "UPDATE consult_conditions SET"
                                + " source='ASKED',asked_message_id=?,answered_message_id=NULL"
                                + " WHERE consult_request_id=? AND condition_key=?",
                        links.clarificationMessageId(),
                        old.requestId(),
                        key);
            }
            if (isAnswered) {
                jdbc.update(
                        "UPDATE consult_conditions SET"
                                + " source='ASKED',answered_message_id=? WHERE"
                                + " consult_request_id=? AND condition_key=?",
                        links.answerMessageId(),
                        old.requestId(),
                        key);
            }
        }
    }

    /** 최종 답변 저장 후 호출한다. 해당 상담의 답변인지 호출자가 확인한다. */
    public Snapshot complete(
            long sessionId, long requestId, int expectedVersion, long finalMessageId) {
        return tx.execute(
                status -> {
                    var old = read(sessionId, requestId);
                    requireOpenVersion(old, expectedVersion);
                    if ("WAITING_CONDITION".equals(old.status())
                            || old.conditions().values().stream()
                                    .anyMatch(c -> c.status() == ConditionStatus.PENDING)) {
                        throw new IllegalStateException(
                                "Cannot complete while awaiting conditions");
                    }
                    int matching =
                            jdbc.queryForObject(
                                    "SELECT count(*) FROM chat_messages m JOIN consult_requests r"
                                        + " ON r.consult_request_id=? JOIN chat_messages origin ON"
                                        + " origin.message_id=r.origin_message_id WHERE"
                                        + " m.message_id=? AND m.session_id=r.session_id AND"
                                        + " origin.session_id=r.session_id AND"
                                        + " m.sequence_no>origin.sequence_no AND m.role='ASSISTANT'"
                                        + " AND m.message_type IN ('ANSWER','STORE_RESULT') AND"
                                        + " m.status='COMPLETED'",
                                    Integer.class,
                                    requestId,
                                    finalMessageId);
                    if (matching != 1) {
                        throw new IllegalArgumentException(
                                "Final answer must be a completed later message in this"
                                        + " conversation");
                    }
                    jdbc.update(
                            "UPDATE consult_requests SET"
                                    + " status='DONE',version=version+1,updated_at=now() WHERE"
                                    + " consult_request_id=?",
                            requestId);
                    return read(sessionId, requestId);
                });
    }

    /** 상담만 취소하고 메시지·조건 이력은 남긴다. */
    public Snapshot cancel(long sessionId, long requestId, int expectedVersion) {
        return tx.execute(
                status -> {
                    var old = read(sessionId, requestId);
                    requireOpenVersion(old, expectedVersion);
                    jdbc.update(
                            "UPDATE consult_requests SET"
                                    + " status='CANCELLED',version=version+1,updated_at=now() WHERE"
                                    + " consult_request_id=?",
                            requestId);
                    return read(sessionId, requestId);
                });
    }

    private void requireOpenVersion(Snapshot old, int expectedVersion) {
        if (old.version() != expectedVersion) {
            throw new StateConflict();
        }
        if (Set.of("DONE", "CANCELLED").contains(old.status())) {
            throw new GeneralException(ConsultErrorCode.REQUEST_CLOSED);
        }
    }

    private void checkMessage(long messageId, long sessionId, String role, String type) {
        int count =
                jdbc.queryForObject(
                        "SELECT count(*) FROM chat_messages WHERE message_id=? AND session_id=? AND"
                                + " role=? AND message_type=? AND status='COMPLETED'",
                        Integer.class,
                        messageId,
                        sessionId,
                        role,
                        type);
        if (count != 1) {
            throw new IllegalArgumentException(
                    "Message does not belong to this conversation or has wrong type");
        }
    }
}
