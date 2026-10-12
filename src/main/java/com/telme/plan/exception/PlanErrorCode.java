package com.telme.plan.exception;

import com.telme.global.common.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum PlanErrorCode implements BaseErrorCode {

    PLAN_NOT_FOUND(HttpStatus.NOT_FOUND, "PLAN404-0", "요금제를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
