package com.telme.intent.exception;

import com.telme.global.common.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum IntentErrorCode implements BaseErrorCode {

    ROUTING_NOT_FOUND(
        HttpStatus.NOT_FOUND, "INTENT404-0",
        "해당 메시지의 라우팅 결과를 찾을 수 없습니다."
    ),
    MESSAGE_NOT_FOUND(
        HttpStatus.NOT_FOUND, "INTENT404-1",
        "해당 메시지를 찾을 수 없습니다."
    ),
    LLM_RESPONSE_PARSE_FAILED(
        HttpStatus.INTERNAL_SERVER_ERROR, "INTENT500-0",
        "LLM 응답 JSON 파싱에 실패했습니다."
    ),
    LLM_CONNECTION_FAILED(
        HttpStatus.SERVICE_UNAVAILABLE, "INTENT503-0",
        "LLM 서버에 연결할 수 없습니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}
