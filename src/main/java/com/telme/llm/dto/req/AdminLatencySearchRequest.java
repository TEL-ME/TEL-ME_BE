package com.telme.llm.dto.req;

import java.time.Duration;
import java.time.Instant;

public record AdminLatencySearchRequest(Instant from, Instant to) {

    private static final Duration DEFAULT_PERIOD = Duration.ofHours(24);
    
    public Instant toOr(Instant now) {
        return to == null ? now : to;
    }
    
    public Instant fromOr(Instant now) {
        return from == null ? toOr(now).minus(DEFAULT_PERIOD) : from;
    }
}
