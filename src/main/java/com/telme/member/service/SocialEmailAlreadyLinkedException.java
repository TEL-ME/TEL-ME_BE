package com.telme.member.service;

import lombok.Getter;

// provider 무관 — 소셜 로그인 이메일이 기존 이메일 회원과 일치해 새 회원을 만들지 않고 중단했다는 신호.
// 실제 연결은 자동으로 하지 않는다 — 호출자가 비밀번호 확인 등 본인확인 절차로 넘긴다
@Getter
public class SocialEmailAlreadyLinkedException extends RuntimeException {

    private final Long matchedUserId;
    private final String matchedEmail;

    public SocialEmailAlreadyLinkedException(Long matchedUserId, String matchedEmail) {
        this.matchedUserId = matchedUserId;
        this.matchedEmail = matchedEmail;
    }
}
