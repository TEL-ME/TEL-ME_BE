package com.telme.llm.dto.res;

import java.time.Instant;
import java.util.List;

public record AdminLatencyResponse(
        Instant from,
        Instant to,
        Stats overall,
        Stats firstToken,
        List<TaskStats> tasks
        ) {

    // 건수가 0이면 시간 값은 null이다.
    public record Stats(long count, Long avgMs, Long p50Ms, Long p95Ms) {
    }
    
    public record TaskStats(String taskType, long count, Long avgMs, Long p50Ms, Long p95Ms) {
    }
}
