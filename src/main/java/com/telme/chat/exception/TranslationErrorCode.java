package com.telme.chat.exception;

import com.telme.global.common.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum TranslationErrorCode implements BaseErrorCode {

    NOT_TRANSLATABLE(HttpStatus.BAD_REQUEST, "TRANSLATION400-0", "번역할 수 없는 메시지입니다."),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "TRANSLATION429-0", "번역 요청이 너무 많습니ㅏㄷ. 잠시 후 다시 시도해 주세요."),
    UNVERIFIED(HttpStatus.BAD_GATEWAY, "TRANSLATION502-0", "번역 결과를 확인하지 못해 보여 드릴 수 없습니다.");
    
    private final HttpStatus status;
    private final String code;
    private final String message;
}
