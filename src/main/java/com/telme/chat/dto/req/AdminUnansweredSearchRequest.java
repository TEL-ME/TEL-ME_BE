package com.telme.chat.dto.req;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;

public record AdminUnansweredSearchRequest(
        AdminUnansweredType type,
        Instant from,
        Instant to,
        @Min(0) Integer page,
        @Min(1) @Max(100) Integer size
) {
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    // 조건을 안 준 기간을 대신할 양 끝. null을 넘기면 Postgres가 파라미터 타입을 정하지 못한다
    private static final Instant EVERY_TIME_FROM = Instant.EPOCH;
    private static final Instant EVERY_TIME_TO = Instant.parse("9999-12-31T23:59:59Z");

    public AdminUnansweredSearchRequest {
        page = page == null ? DEFAULT_PAGE : page;
        size = size == null ? DEFAULT_SIZE : size;
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
