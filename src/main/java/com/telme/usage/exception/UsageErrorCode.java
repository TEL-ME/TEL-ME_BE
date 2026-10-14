package com.telme.usage.exception;

import com.telme.global.common.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum UsageErrorCode implements BaseErrorCode {

    MONTH_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "USAGE400-0", "조회할 수 없는 달입니다. 개통 이후 최근 6개월까지 조회할 수 있습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
