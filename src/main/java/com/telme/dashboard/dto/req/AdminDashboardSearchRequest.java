package com.telme.dashboard.dto.req;

import java.time.Instant;

// 기간은 답 못 한 질문 수와 실패한 답변 수에만 걸린다. 미처리 싫어요는 잘라내면 오래된 할 일이 사라진다
public record AdminDashboardSearchRequest(Instant from, Instant to) {

    // 조건을 안 준 기간을 대신할 양 끝. null을 넘기면 Postgres가 파라미터 타입을 정하지 못한다
    private static final Instant EVERY_TIME_FROM = Instant.EPOCH;
    private static final Instant EVERY_TIME_TO = Instant.parse("9999-12-31T23:59:59Z");

    public Instant fromOrMin() {
        return from == null ? EVERY_TIME_FROM : from;
    }

    public Instant toOrMax() {
        return to == null ? EVERY_TIME_TO : to;
    }

    public boolean periodReversed() {
        return from != null && to != null && from.isAfter(to);
    }
}
