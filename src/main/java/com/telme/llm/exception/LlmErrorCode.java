package com.telme.llm.exception;

import com.telme.global.common.code.BaseErrorCode;

import lombok.AllArgsConstructor;
import lombok.Getter;

import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum LlmErrorCode implements BaseErrorCode {

    CONNECTION_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "LLM503-0", "LLM 서버에 연결할 수 없습니다."),
    TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "LLM504-0", "LLM 응답 시간이 초과되었습니다."),
    MODEL_ERROR(HttpStatus.BAD_GATEWAY, "LLM502-0", "LLM 서버가 오류를 반환했습니다."),
    INVALID_RESPONSE(HttpStatus.BAD_GATEWAY, "LLM502-1", "LLM 응답 형식이 올바르지 않습니다."),
    STREAM_INTERRUPTED(HttpStatus.BAD_GATEWAY, "LLM502-2", "LLM 응답이 완료되기 전에 끊겼습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
