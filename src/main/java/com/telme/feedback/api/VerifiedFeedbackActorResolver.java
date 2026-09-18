package com.telme.feedback.api;

import com.telme.feedback.dto.FeedbackModels.Actor;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 회원팀 어댑터: 로그인 세션/게스트 쿠키의 유효성·만료를 검증한 신원만 반환한다. 요청 본문·쿼리·검증 안 된 헤더의 userId/guestId를 신원으로 사용하면 안 된다.
 * 인증 불가 시 null을 반환한다. 로컬 테스트 이외의 구현은 회원팀 규격 합의 후 연결한다.
 */
@FunctionalInterface
public interface VerifiedFeedbackActorResolver {
    Actor resolve(HttpServletRequest request);
}
