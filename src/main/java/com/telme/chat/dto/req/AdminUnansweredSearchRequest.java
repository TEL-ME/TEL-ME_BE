package com.telme.chat.dto.req;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.List;

public record AdminUnansweredSearchRequest(
        List<AdminUnansweredType> type,
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
        type = type == null ? List.of() : List.copyOf(type);
        page = page == null ? DEFAULT_PAGE : page;
        size = size == null ? DEFAULT_SIZE : size;
    }

    public List<AdminUnansweredType> basisTypes() {
        return type.stream().filter(AdminUnansweredType::isBasis).distinct().toList();
    }

    public List<AdminUnansweredType> statusTypes() {
        return type.stream().filter(t -> !t.isBasis()).distinct().toList();
    }

    public Instant fromOrMin() {
        return from == null ? EVERY_TIME_FROM : from;
    }

    // 기준은 답변 메시지가 만들어진 시각이다. 완료 시각은 비어 있을 수 있어 쓰면 그 행이 조용히 빠진다.
    // to는 그 시각 직전까지를 뜻한다. 경계를 포함하면 자정으로 끊어 보는 화면에서 하루가 겹쳐 보인다
    public Instant toOrMax() {
        return to == null ? EVERY_TIME_TO : to;
    }

    public boolean periodReversed() {
        return from != null && to != null && from.isAfter(to);
    }
}
