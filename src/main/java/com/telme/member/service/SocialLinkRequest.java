package com.telme.member.service;

import com.telme.member.entity.SocialAccount;
import java.time.Duration;
import java.time.Instant;

// provider는 연결을 시작한 공급자 — 인가 요청과 콜백이 같은 공급자인지 대조해 다른 공급자 계정이 붙는 것을 막는다
public record SocialLinkRequest(
        Long targetUserId, SocialAccount.Provider provider, Instant issuedAt, String startToken, String state) {

    public boolean isExpired(Instant now, Duration ttl) {
        return !issuedAt.plus(ttl).isAfter(now);
    }
}
