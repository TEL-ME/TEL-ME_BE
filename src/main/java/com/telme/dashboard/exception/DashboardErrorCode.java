package com.telme.dashboard.exception;

import com.telme.global.common.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum DashboardErrorCode implements BaseErrorCode {

    INVALID_PERIOD(HttpStatus.BAD_REQUEST, "DASHBOARD400-0", "조회 기간의 시작이 끝보다 늦습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
