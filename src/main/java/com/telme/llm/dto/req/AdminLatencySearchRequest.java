package com.telme.llm.dto.req;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
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
    
    @JsonIgnore
    @AssertTrue(message = "시작 시각은 끝 시각보다 빨라야 합니다.")
    public boolean isPeriodValid() {
        return from == null || to == null || from.isBefore(to);
    }
}
