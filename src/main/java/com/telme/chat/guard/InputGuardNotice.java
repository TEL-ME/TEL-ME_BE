package com.telme.chat.guard;

import java.time.Instant;
import java.util.List;

/** 생성 실패와 구분되는 정책 안내. 실행을 만들지 않은 결과에는 실행 ID가 없다. */
public record InputGuardNotice(
        String action,
        String message,
        int violationCount,
        long retryAfterSeconds,
        Instant restrictionStartedAt,
        Instant restrictionUntil,
        List<InputInspection.Detection> detections) {
    public InputGuardNotice {
        detections = List.copyOf(detections);
    }
}
