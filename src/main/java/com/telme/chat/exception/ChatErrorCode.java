package com.telme.chat.exception;

import com.telme.global.common.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ChatErrorCode implements BaseErrorCode {

    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "CHAT401-0", "채팅 이용을 위한 인증 정보가 없습니다."),
    INVALID_CURSOR(HttpStatus.BAD_REQUEST, "CHAT400-0", "유효하지 않은 채팅 목록 커서입니다."),
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "CHAT404-0", "채팅 세션을 찾을 수 없습니다."),
    EXECUTION_NOT_FOUND(HttpStatus.NOT_FOUND, "CHAT404-1", "채팅 실행 이력을 찾을 수 없습니다."),
    SESSION_CLOSED(HttpStatus.CONFLICT, "CHAT409-0", "종료된 채팅 세션에는 메시지를 보낼 수 없습니다."),
    EXECUTION_NOT_RUNNING(HttpStatus.CONFLICT, "CHAT409-1", "이미 종료된 채팅 실행입니다."),
    ANSWER_ALREADY_STARTED(HttpStatus.CONFLICT, "CHAT409-2", "이미 답변 생성을 시작한 채팅 실행입니다."),
    EXECUTION_IN_PROGRESS(HttpStatus.CONFLICT, "CHAT409-3", "이전 질문에 대한 답변을 생성하고 있습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
