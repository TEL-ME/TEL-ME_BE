package com.telme.member.service;

import java.time.Duration;
import java.time.Instant;

public record KakaoLinkRequest(Long targetUserId, Instant issuedAt, String startToken, String state) {

    public boolean isExpired(Instant now, Duration ttl) {
        return !issuedAt.plus(ttl).isAfter(now);
    }
}
