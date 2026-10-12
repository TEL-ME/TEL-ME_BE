package com.telme.chat.guard;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "chat.input-guard")
public record InputGuardProperties(
        @DefaultValue("PT10M") Duration observationWindow,
        @DefaultValue("3") int threshold,
        @DefaultValue("PT1M") Duration restrictionDuration,
        @DefaultValue("P30D") Duration retention) {
    public InputGuardProperties {
        if (observationWindow == null
                || observationWindow.isNegative()
                || observationWindow.isZero()
                || restrictionDuration == null
                || restrictionDuration.isNegative()
                || restrictionDuration.isZero()
                || retention == null
                || retention.compareTo(observationWindow) < 0
                || threshold < 2) {
            throw new IllegalArgumentException("입력 검사 집계 기간·횟수·제한 시간·보존 기간을 확인해 주세요.");
        }
    }
}
