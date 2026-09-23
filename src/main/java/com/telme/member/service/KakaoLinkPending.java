package com.telme.member.service;

import java.time.Duration;
import java.time.Instant;

// 이메일 로그인 상태에서 카카오 연결을 시작했다는 표시 — 콜백이 실제로 그 시도에서 온 것인지
// (다른 탭에서 시작된 일반 로그인이 아닌지) 세션의 USER_ID_ATTRIBUTE와 대조해 확인한다
public record KakaoLinkPending(Long targetUserId, Instant issuedAt) {

    public boolean isExpired(Instant now, Duration ttl) {
        return issuedAt.plus(ttl).isBefore(now);
    }
}
