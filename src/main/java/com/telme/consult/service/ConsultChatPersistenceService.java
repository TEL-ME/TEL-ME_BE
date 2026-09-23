package com.telme.consult.service;

import com.telme.chat.service.ChatAnswer;
import com.telme.chat.service.ChatExecutionService;
import com.telme.chat.service.ChatExecutionState;
import com.telme.chat.service.ChatFailure;
import com.telme.consult.dto.DialogueDecision.Action;
import com.telme.consult.exception.ConsultErrorCode;
import com.telme.consult.repository.JdbcConsultStateStore;
import com.telme.consult.repository.JdbcConsultStateStore.MessageLinks;
import com.telme.global.common.exception.GeneralException;
import com.telme.rag.dto.res.AnswerResult.AnswerSource;
import com.telme.rag.service.AnswerSourcesReady;

import lombok.RequiredArgsConstructor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "telme.consult.persistence-enabled", havingValue = "true")
public class ConsultChatPersistenceService {
    private final JdbcTemplate jdbc;
    private final ChatExecutionService chatExecutionService;
    private final ConsultService consultService;
    private final JdbcConsultStateStore stateStore;
    private final ApplicationEventPublisher eventPublisher;

    // 실제 답변 생성을 시작하기 전에 GENERATING 메시지를 먼저 만든다.
    // 이후 completeAnswer/fail은 같은 메시지를 완료 또는 실패 상태로 갱신한다.
    @Transactional
    public ChatExecutionState startAnswer(long executionId, long sessionId) {
        ChatExecutionState state = chatExecutionService.startAnswer(executionId);
        if (state.sessionId() != sessionId || state.outputMessage() == null) {
            throw new GeneralException(ConsultErrorCode.STATE_CONFLICT);
        }
        return state;
    }

    @Transactional
    public ChatExecutionState failAnswer(
            long executionId, long sessionId, ChatFailure failure) {
        Objects.requireNonNull(failure, "failure");
        ChatExecutionState state = chatExecutionService.fail(executionId, failure);
        if (state.sessionId() != sessionId || state.outputMessage() == null) {
            throw new GeneralException(ConsultErrorCode.STATE_CONFLICT);
        }
        return state;
    }

    // 답변 저장에 성공했을 때만 상담도 완료한다.
    @Transactional
    public ChatExecutionState persistFinalAnswer(
            long executionId,
            long sessionId,
            long consultRequestId,
            int expectedVersion,
            ChatAnswer answer) {
        return persistFinalAnswer(
                executionId,
                sessionId,
                consultRequestId,
                expectedVersion,
                answer,
                List.of());
    }

    @Transactional
    public ChatExecutionState persistFinalAnswer(
            long executionId,
            long sessionId,
            long consultRequestId,
            int expectedVersion,
            ChatAnswer answer,
            List<AnswerSource> sources) {
        Objects.requireNonNull(answer, "answer");
        sources = List.copyOf(Objects.requireNonNull(sources, "sources"));
        var sessions =
                jdbc.queryForList(
                        "SELECT session_id FROM chat_executions WHERE execution_id=? FOR UPDATE",
                        Long.class,
                        executionId);
        if (sessions.isEmpty() || sessions.getFirst() != sessionId) {
            throw new GeneralException(ConsultErrorCode.STATE_CONFLICT);
        }
        ChatExecutionState state = chatExecutionService.completeAnswer(executionId, answer);
        if (state.outputMessage() == null) {
            throw new GeneralException(ConsultErrorCode.STATE_CONFLICT);
        }
        stateStore.complete(
                sessionId,
                consultRequestId,
                expectedVersion,
                state.outputMessage().messageId());
        if (!sources.isEmpty()) {
            eventPublisher.publishEvent(
                    new AnswerSourcesReady(state.outputMessage().messageId(), sources));
        }
        return state;
    }

    // 기존 질문은 그대로 두고 조건 변경과 실행 완료만 함께 저장한다.
    @Transactional
    public ChatExecutionState persistWaiting(
            long executionId, long sessionId, ConsultService.PreparationResult result) {
        if (result == null
                || !result.waitingForReply()
                || result.prepared() != null && result.prepared().sessionId() != sessionId) {
            throw new IllegalArgumentException("기존 질문 대기 결과가 필요합니다.");
        }
        var sessions =
                jdbc.queryForList(
                        "SELECT session_id FROM chat_executions WHERE execution_id=? FOR UPDATE",
                        Long.class,
                        executionId);
        if (sessions.isEmpty() || sessions.getFirst() != sessionId) {
            throw new GeneralException(ConsultErrorCode.STATE_CONFLICT);
        }
        ChatExecutionState execution = chatExecutionService.completeWithoutOutput(executionId);
        Integer count =
                jdbc.queryForObject(
                        """
                        SELECT count(*) FROM consult_conditions c
                        JOIN consult_requests r ON r.consult_request_id=c.consult_request_id
                        JOIN chat_messages m ON m.message_id=c.asked_message_id
                        JOIN chat_sessions s ON s.session_id=m.session_id
                        WHERE r.session_id=? AND m.session_id=? AND m.message_id=?
                          AND r.status='WAITING_CONDITION' AND c.status='PENDING'
                          AND m.role='ASSISTANT' AND m.message_type='CLARIFICATION'
                          AND m.status='COMPLETED' AND s.status='NEED_CLARIFICATION'
                        """,
                        Integer.class,
                        sessionId,
                        sessionId,
                        result.pendingMessageId());
        if (count == null || count == 0) {
            throw new GeneralException(ConsultErrorCode.STATE_CONFLICT);
        }
        if (result.prepared() != null) {
            consultService.persistWaitingChanges(result);
        }
        return execution;
    }

    // 준비·모델 호출은 끝내고, 메시지와 상담 상태 저장만 함께 묶는다.
    @Transactional
    public ChatExecutionState persistClarification(
            long executionId, ConsultService.PreparedTurn prepared) {
        return persistClarification(executionId, prepared, null);
    }

    @Transactional
    public ChatExecutionState persistClarification(
            long executionId, ConsultService.PreparedTurn prepared, String answeredField) {
        if (prepared == null || prepared.decision().action() != Action.ASK) {
            throw new IllegalArgumentException("저장할 되묻기 결과가 필요합니다.");
        }
        if (answeredField != null && answeredField.isBlank()) {
            throw new IllegalArgumentException("반영할 후속 답변 조건이 필요합니다.");
        }
        var sessions =
                jdbc.queryForList(
                        "SELECT session_id FROM chat_executions WHERE execution_id=? FOR UPDATE",
                        Long.class,
                        executionId);
        if (sessions.isEmpty() || sessions.getFirst() != prepared.sessionId()) {
            throw new GeneralException(ConsultErrorCode.STATE_CONFLICT);
        }
        ChatExecutionState state =
                chatExecutionService.askClarification(executionId, prepared.decision().message());
        if (state.outputMessage() == null) {
            throw new GeneralException(ConsultErrorCode.STATE_CONFLICT);
        }
        Long inputMessageId =
                answeredField == null
                        ? null
                        : jdbc.queryForObject(
                                "SELECT input_message_id FROM chat_executions WHERE execution_id=?",
                                Long.class,
                                executionId);
        consultService.persist(
                prepared,
                new MessageLinks(
                        state.outputMessage().messageId(), inputMessageId, answeredField));
        return state;
    }

    // 조건은 채웠지만 검색·최종 답변이 남아 있으므로 실행은 종료하지 않는다.
    @Transactional
    public void persistReadyFollowup(
            long executionId, ConsultService.PreparedTurn prepared, String answeredField) {
        if (answeredField == null || answeredField.isBlank()) {
            throw new IllegalArgumentException("반영할 후속 답변 조건이 필요합니다.");
        }
        persistReadyTurn(executionId, prepared, answeredField);
    }

    @Transactional
    public void persistReadyTurn(
            long executionId, ConsultService.PreparedTurn prepared, String answeredField) {
        if (prepared == null
                || prepared.decision().action() == Action.ASK
                || answeredField != null && answeredField.isBlank()) {
            throw new IllegalArgumentException("진행할 상담 판단이 필요합니다.");
        }
        var inputs =
                jdbc.queryForList(
                        """
                        SELECT e.input_message_id FROM chat_executions e
                        JOIN chat_messages m ON m.message_id=e.input_message_id
                        WHERE e.execution_id=? AND e.session_id=? AND m.session_id=?
                          AND e.status='RUNNING' AND m.role='USER'
                          AND m.message_type='QUESTION' AND m.status='COMPLETED'
                        FOR UPDATE OF e
                        """,
                        Long.class,
                        executionId,
                        prepared.sessionId(),
                        prepared.sessionId());
        if (inputs.isEmpty()) {
            throw new GeneralException(ConsultErrorCode.STATE_CONFLICT);
        }
        var sessions =
                jdbc.queryForList(
                        "SELECT status FROM chat_sessions WHERE session_id=? FOR UPDATE",
                        String.class,
                        prepared.sessionId());
        if (sessions.isEmpty() || "CLOSED".equals(sessions.getFirst())) {
            throw new GeneralException(ConsultErrorCode.STATE_CONFLICT);
        }
        consultService.persist(
                prepared,
                new MessageLinks(
                        null, answeredField == null ? null : inputs.getFirst(), answeredField));
    }
}
