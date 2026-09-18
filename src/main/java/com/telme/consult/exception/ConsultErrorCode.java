package com.telme.consult.exception;

import com.telme.global.common.code.BaseErrorCode;

import lombok.AllArgsConstructor;
import lombok.Getter;

import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ConsultErrorCode implements BaseErrorCode {
    REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "CONSULT404-0", "상담을 찾을 수 없습니다."),
    STATE_CONFLICT(HttpStatus.CONFLICT, "CONSULT409-0", "상담 조건이 변경되었습니다. 최신 상태로 다시 처리해주세요."),
    REQUEST_CLOSED(HttpStatus.CONFLICT, "CONSULT409-1", "이미 종료된 상담입니다. 새 상담으로 요청해주세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
