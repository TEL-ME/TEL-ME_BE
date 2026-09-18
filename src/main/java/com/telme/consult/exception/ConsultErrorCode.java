package com.telme.consult.exception;

import com.telme.global.common.code.BaseErrorCode;

import lombok.AllArgsConstructor;
import lombok.Getter;

import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ConsultErrorCode implements BaseErrorCode {
    STATE_CONFLICT(HttpStatus.CONFLICT, "CONSULT409-0", "상담 조건이 변경되었습니다. 최신 상태로 다시 처리해주세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
