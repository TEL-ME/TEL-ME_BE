package com.telme.member.service;

import com.telme.member.entity.SocialAccount;
import java.time.Duration;
import java.time.Instant;

// 소셜 로그인 중 이메일이 일치하는 기존 회원을 발견했을 때, 실제 연결(비밀번호 확인)까지 세션에 들고 있는 임시 상태.
// 연결하지 않고 새 회원으로 시작할 때도 이 상태만 쓰므로 새 회원 이름(nickname)도 함께 둔다
public record SocialEmailMatch(
        SocialAccount.Provider provider, String providerUserId, Long matchedUserId,
        String matchedEmail, String nickname, Instant issuedAt, int failedAttempts) {

    public static SocialEmailMatch issue(
            SocialAccount.Provider provider, String providerUserId,
            Long matchedUserId, String matchedEmail, String nickname, Instant now) {
        return new SocialEmailMatch(provider, providerUserId, matchedUserId, matchedEmail, nickname, now, 0);
    }

    public SocialEmailMatch withFailedAttempt() {
        return new SocialEmailMatch(
                provider, providerUserId, matchedUserId, matchedEmail, nickname, issuedAt, failedAttempts + 1);
    }

    public boolean isExpired(Instant now, Duration ttl) {
        return issuedAt.plus(ttl).isBefore(now);
    }
}
