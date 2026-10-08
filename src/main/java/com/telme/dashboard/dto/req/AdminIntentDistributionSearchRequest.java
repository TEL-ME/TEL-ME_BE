package com.telme.dashboard.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record AdminIntentDistributionSearchRequest(
        @Schema(description = "분류 기록 시각의 시작(포함). 생략하면 전체 과거", example = "2026-10-01T00:00:00+09:00")
        Instant from,
        @Schema(description = "분류 기록 시각의 끝(제외). 생략하면 전체 미래", example = "2026-10-08T00:00:00+09:00")
        Instant to) {

    public Instant fromOrMin() {
        return from == null ? Instant.EPOCH : from;
    }

    public Instant toOrMax() {
        return to == null ? Instant.parse("9999-12-31T23:59:59Z") : to;
    }

    public boolean periodReversed() {
        return fromOrMin().isAfter(toOrMax());
    }
}
