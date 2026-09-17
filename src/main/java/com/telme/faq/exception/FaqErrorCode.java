package com.telme.faq.exception;

import com.telme.global.common.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum FaqErrorCode implements BaseErrorCode {

    EMBEDDING_RESPONSE_INVALID(HttpStatus.INTERNAL_SERVER_ERROR, "FAQ500-0", "임베딩 응답이 올바르지 않습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
