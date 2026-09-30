package com.telme.feedback.dto.req;

import com.telme.feedback.entity.MessageFeedback;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.List;

public record AdminFeedbackSearchRequest(
        MessageFeedback.ReasonCode reason,
        AdminFeedbackHandledFilter handled,
        Instant from,
        Instant to,
        @Min(0) Integer page,
        @Min(1) @Max(100) Integer size
) {
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final List<MessageFeedback.ReasonCode> EVERY_REASON =
            List.of(MessageFeedback.ReasonCode.values());
    // 조건을 안 준 기간을 대신할 양 끝. timestamptz가 담을 수 있는 범위 안에 둔다
    private static final Instant EVERY_TIME_FROM = Instant.EPOCH;
    private static final Instant EVERY_TIME_TO = Instant.parse("9999-12-31T23:59:59Z");

    public AdminFeedbackSearchRequest {
        // 관리자가 들어오자마자 남은 일만 보이게 한다
        handled = handled == null ? AdminFeedbackHandledFilter.UNHANDLED : handled;
        page = page == null ? DEFAULT_PAGE : page;
        size = size == null ? DEFAULT_SIZE : size;
    }

    // 조건을 안 준 항목은 전체로 넓혀 넘긴다. null을 그대로 보내면
    // Postgres가 "? is null" 한 줄만 보고는 파라미터 타입을 정하지 못한다
    public List<MessageFeedback.ReasonCode> reasons() {
        return reason == null ? EVERY_REASON : List.of(reason);
    }

    public Instant fromOrMin() {
        return from == null ? EVERY_TIME_FROM : from;
    }

    // to는 그 시각 직전까지를 뜻한다. 경계를 포함하면 자정으로 끊어 보는 화면에서 하루가 겹쳐 보인다
    public Instant toOrMax() {
        return to == null ? EVERY_TIME_TO : to;
    }

    public boolean periodReversed() {
        return from != null && to != null && from.isAfter(to);
    }
}
