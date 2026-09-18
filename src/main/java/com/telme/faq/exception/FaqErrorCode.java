package com.telme.faq.exception;

import com.telme.global.common.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum FaqErrorCode implements BaseErrorCode {

    EMBEDDING_RESPONSE_INVALID(HttpStatus.INTERNAL_SERVER_ERROR, "FAQ500-0", "임베딩 응답이 올바르지 않습니다."),
    EMBEDDING_REQUEST_REJECTED(HttpStatus.INTERNAL_SERVER_ERROR, "FAQ500-1", "임베딩 요청이 Ollama에서 거부되었습니다."),
    EMBEDDING_REQUEST_INVALID(HttpStatus.BAD_REQUEST, "FAQ400-0", "임베딩 요청이 올바르지 않습니다."),
    EMBEDDING_REQUEST_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "FAQ503-0", "임베딩 서버 호출에 실패했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
