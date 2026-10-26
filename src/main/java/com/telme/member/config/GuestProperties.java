package com.telme.member.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "member.guest")
public record GuestProperties(Duration ttl) {

    public GuestProperties {
        if (ttl == null) {
            throw new IllegalArgumentException("member.guest.ttl 설정이 필요합니다.");
        }
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("member.guest.ttl은 0보다 커야 합니다: " + ttl);
        }
    }
}
