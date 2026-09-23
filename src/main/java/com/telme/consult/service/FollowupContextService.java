package com.telme.consult.service;

import com.telme.chat.exception.ChatErrorCode;
import com.telme.chat.service.ChatActor;
import com.telme.chat.service.ChatContext;
import com.telme.consult.exception.ConsultErrorCode;
import com.telme.consult.repository.PendingClarificationFinder;
import com.telme.consult.repository.PendingClarificationFinder.Candidate;
import com.telme.global.common.exception.GeneralException;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Objects;

/** 후속 답변 분석용 문맥을 준비한다. 기존 상담에 연결할지는 분석 쪽에서 판단한다. */
public final class FollowupContextService {
    private final JdbcTemplate jdbc;
    private final PendingClarificationFinder finder;
    private final TransactionTemplate transaction;

    public FollowupContextService(
            JdbcTemplate jdbc, PendingClarificationFinder finder, TransactionTemplate transaction) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.finder = Objects.requireNonNull(finder);
        this.transaction = Objects.requireNonNull(transaction);
    }

    public record Context(
            long sessionId,
            long userMessageId,
            String message,
            List<Candidate> candidates,
            ChatContext routingContext) {
        public Context(
                long sessionId,
                long userMessageId,
                String message,
                List<Candidate> candidates) {
            this(sessionId, userMessageId, message, candidates, null);
        }

        public Context {
            Objects.requireNonNull(message, "message");
            candidates = List.copyOf(candidates);
        }

        public Context withRoutingContext(ChatContext value) {
            return new Context(sessionId, userMessageId, message, candidates, value);
        }
    }

    public Context prepare(ChatActor actor, long sessionId, long userMessageId) {
        if (actor == null || (actor.userId() == null && actor.guestId() == null)) {
            throw new GeneralException(ChatErrorCode.UNAUTHENTICATED);
        }
        if (sessionId <= 0 || userMessageId <= 0) {
            throw new IllegalArgumentException("채팅방과 사용자 메시지 ID가 필요합니다.");
        }
        return transaction.execute(
                status -> {
                    // 소유권 확인부터 후보 조회까지 세션 잠금을 유지한다.
                    var owners =
                            jdbc.query(
                                    "SELECT user_id,guest_id,status FROM chat_sessions WHERE"
                                            + " session_id=? FOR UPDATE",
                                    (rs, n) ->
                                            new Owner(
                                                    rs.getObject("user_id", Long.class),
                                                    rs.getObject("guest_id", java.util.UUID.class),
                                                    rs.getString("status")),
                                    sessionId);
                    if (owners.isEmpty()) {
                        throw new GeneralException(ChatErrorCode.SESSION_NOT_FOUND);
                    }
                    var owner = owners.getFirst();
                    boolean owns =
                            actor.isMember()
                                    ? actor.userId().equals(owner.userId())
                                    : owner.userId() == null
                                            && actor.guestId().equals(owner.guestId());
                    if (!owns) {
                        throw new GeneralException(ChatErrorCode.SESSION_NOT_FOUND);
                    }
                    if ("CLOSED".equals(owner.status())) {
                        throw new GeneralException(ChatErrorCode.SESSION_CLOSED);
                    }
                    var messages =
                            jdbc.queryForList(
                                    "SELECT content FROM chat_messages WHERE session_id=? AND"
                                            + " message_id=? AND role='USER' AND"
                                            + " message_type='QUESTION' AND status='COMPLETED'",
                                    String.class,
                                    sessionId,
                                    userMessageId);
                    if (messages.isEmpty()) {
                        throw new GeneralException(ConsultErrorCode.STATE_CONFLICT);
                    }
                    return new Context(
                            sessionId,
                            userMessageId,
                            messages.getFirst(),
                            finder.findBefore(sessionId, userMessageId),
                            null);
                });
    }

    private record Owner(Long userId, java.util.UUID guestId, String status) {}
}
