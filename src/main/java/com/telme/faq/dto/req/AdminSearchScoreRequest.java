package com.telme.faq.dto.req;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import java.time.Instant;

// 프론트 기간 선택(최근 7일·30일·전체)은 from만 보낸다. 비우면 처음부터, to를 비우면 지금까지다
public record AdminSearchScoreRequest(Instant from, Instant to) {

    private static final Instant EVERY_TIME_FROM = Instant.EPOCH;
    private static final Instant EVERY_TIME_TO = Instant.parse("9999-12-31T23:59:59Z");
    
    public Instant fromOrMin() {
        return from == null ? EVERY_TIME_FROM : from;
    }
    
    public Instant toOrMax() {
        return to == null ? EVERY_TIME_TO : to;
    }
    
    @JsonIgnore
    @AssertTrue(message = "시작 시각은 끝 시각보다 빨라야 합니다.")
    public boolean isPeriodValid() {
        return fromOrMin().isBefore(toOrMax());
    }
}
