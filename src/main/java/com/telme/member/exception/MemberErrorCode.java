package com.telme.member.exception;

import com.telme.global.common.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum MemberErrorCode implements BaseErrorCode {

    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "MEMBER409-0", "이미 가입된 이메일입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "MEMBER401-0", "이메일 또는 비밀번호가 올바르지 않습니다."),
    ACCOUNT_SUSPENDED(HttpStatus.FORBIDDEN, "MEMBER403-0", "이용이 제한된 계정입니다."),
    ACCOUNT_WITHDRAWN(HttpStatus.FORBIDDEN, "MEMBER403-1", "탈퇴한 계정입니다."),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "MEMBER401-1", "로그인이 필요합니다."),
    KAKAO_LINK_SESSION_EXPIRED(HttpStatus.BAD_REQUEST, "MEMBER400-0", "카카오 계정 연결 요청이 만료되었거나 존재하지 않습니다. 다시 시도해주세요."),
    EMAIL_LINK_REQUIRED(HttpStatus.CONFLICT, "MEMBER409-1", "이미 가입된 이메일 계정이 있어 비밀번호 확인 후 연결해야 합니다."),
    SOCIAL_ACCOUNT_ALREADY_LINKED(HttpStatus.CONFLICT, "MEMBER409-2", "이미 다른 소셜 계정이 연결되어 있습니다."),
    EMAIL_LOGIN_ALREADY_SET(HttpStatus.CONFLICT, "MEMBER409-3", "이미 이메일 로그인이 등록되어 있습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
