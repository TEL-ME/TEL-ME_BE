package com.telme.consult.service;

import com.telme.chat.exception.ChatErrorCode;
import com.telme.chat.service.ChatActor;
import com.telme.chat.service.ChatContext;
import com.telme.chat.service.ChatContextBuilder;
import com.telme.chat.service.ChatProcessingCommand;
import com.telme.consult.exception.ConsultErrorCode;
import com.telme.consult.service.ConsultTurnAnalysisAdapter.ContextProvider;
import com.telme.consult.service.FollowupContextService.Context;
import com.telme.global.common.exception.GeneralException;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;

/** Chat 실행과 입력 메시지 관계를 확인한 뒤 상담 분석용 문맥을 만든다. */
@Slf4j
public final class ChatCommandContextProvider implements ContextProvider {
    private static final int ROUTING_CONTEXT_TOKEN_BUDGET = 1024;

    private final JdbcTemplate jdbc;
    private final FollowupContextService contexts;
    private final TransactionTemplate transaction;
    private final ChatContextBuilder chatContextBuilder;

    public ChatCommandContextProvider(
            JdbcTemplate jdbc,
            FollowupContextService contexts,
            TransactionTemplate transaction) {
        this(jdbc, contexts, transaction, null);
    }

    public ChatCommandContextProvider(
            JdbcTemplate jdbc,
            FollowupContextService contexts,
            TransactionTemplate transaction,
            ChatContextBuilder chatContextBuilder) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.contexts = Objects.requireNonNull(contexts);
        this.transaction = Objects.requireNonNull(transaction);
        this.chatContextBuilder = chatContextBuilder;
    }

    @Override
    public Context loadVerified(ChatProcessingCommand command) {
        validate(command);
        Context verified = transaction.execute(
                status -> {
                    var executions =
                            jdbc.query(
                                    """
                                    SELECT e.status, s.user_id, s.guest_id, m.content
                                    FROM chat_executions e
                                    JOIN chat_sessions s ON s.session_id=e.session_id
                                    JOIN chat_messages m ON m.message_id=e.input_message_id
                                    WHERE e.execution_id=? AND e.session_id=?
                                      AND e.input_message_id=? AND m.session_id=e.session_id
                                    FOR UPDATE OF e
                                    """,
                                    (rs, rowNumber) ->
                                            new ExecutionContext(
                                                    rs.getString("status"),
                                                    rs.getObject("user_id", Long.class),
                                                    rs.getObject("guest_id", UUID.class),
                                                    rs.getString("content")),
                                    command.executionId(),
                                    command.sessionId(),
                                    command.inputMessageId());
                    if (executions.isEmpty()) {
                        throw new GeneralException(ChatErrorCode.EXECUTION_NOT_FOUND);
                    }
                    var execution = executions.getFirst();
                    if (!"RUNNING".equals(execution.status())) {
                        throw new GeneralException(ChatErrorCode.EXECUTION_NOT_RUNNING);
                    }
                    if (!Objects.equals(execution.content(), command.content())) {
                        throw new GeneralException(ConsultErrorCode.STATE_CONFLICT);
                    }
                    return contexts.prepare(
                            execution.actor(), command.sessionId(), command.inputMessageId());
                });
        if (verified == null || chatContextBuilder == null) {
            return verified;
        }
        try {
            ChatContext routingContext =
                    chatContextBuilder.build(command, ROUTING_CONTEXT_TOKEN_BUDGET);
            return verified.withRoutingContext(routingContext);
        } catch (RuntimeException exception) {
            log.warn(
                    "라우팅 문맥 구성 실패, 현재 질문만 사용: executionId={}, reason={}",
                    command.executionId(),
                    exception.getMessage());
            return verified;
        }
    }

    private void validate(ChatProcessingCommand command) {
        Objects.requireNonNull(command, "command");
        if (command.executionId() == null
                || command.executionId() <= 0
                || command.sessionId() == null
                || command.sessionId() <= 0
                || command.inputMessageId() == null
                || command.inputMessageId() <= 0
                || command.content() == null
                || command.content().isBlank()) {
            throw new IllegalArgumentException("상담 문맥을 조회할 Chat 실행 정보가 필요합니다.");
        }
    }

    private record ExecutionContext(String status, Long userId, UUID guestId, String content) {
        private ExecutionContext {
            Objects.requireNonNull(status, "status");
            if (userId == null && guestId == null) {
                throw new GeneralException(ConsultErrorCode.STATE_CONFLICT);
            }
        }

        private ChatActor actor() {
            return userId == null ? new ChatActor(null, guestId) : new ChatActor(userId, null);
        }
    }
}
