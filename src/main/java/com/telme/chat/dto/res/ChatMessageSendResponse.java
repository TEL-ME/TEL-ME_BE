package com.telme.chat.dto.res;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.telme.chat.guard.InputGuardNotice;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatMessageSendResponse(
        Long sessionId,
        Long messageId,
        Integer sequenceNo,
        Long executionId,
        String executionStatus,
        Instant createdAt,
        InputGuardNotice inputGuard,
        // 게스트가 이 질문을 접수한 뒤 오늘 남은 질문 수. 회원이거나 접수하지 않은 응답에는 없다
        Integer guestQuestionsRemaining
) {

    public ChatMessageSendResponse(Long sessionId, Long messageId, Integer sequenceNo,
            Long executionId, String executionStatus, Instant createdAt) {
        this(sessionId, messageId, sequenceNo, executionId, executionStatus, createdAt, null, null);
    }

    public ChatMessageSendResponse(Long sessionId, Long messageId, Integer sequenceNo,
            Long executionId, String executionStatus, Instant createdAt, InputGuardNotice inputGuard) {
        this(sessionId, messageId, sequenceNo, executionId, executionStatus, createdAt, inputGuard, null);
    }

    public ChatMessageSendResponse withInputGuard(InputGuardNotice notice) {
        return new ChatMessageSendResponse(sessionId, messageId, sequenceNo, executionId,
                executionStatus, createdAt, notice, guestQuestionsRemaining);
    }

    public ChatMessageSendResponse withGuestQuestionsRemaining(Integer remaining) {
        return new ChatMessageSendResponse(sessionId, messageId, sequenceNo, executionId,
                executionStatus, createdAt, inputGuard, remaining);
    }

    public boolean accepted() {
        return executionId != null;
    }
}
