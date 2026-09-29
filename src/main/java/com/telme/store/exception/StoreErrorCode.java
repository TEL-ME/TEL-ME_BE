package com.telme.store.exception;

import com.telme.global.common.code.BaseErrorCode;

import lombok.AllArgsConstructor;
import lombok.Getter;

import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum StoreErrorCode implements BaseErrorCode{

    STORE_NOT_FOUND(HttpStatus.NOT_FOUND, "STORE404-0", "매장을 찾을 수 없습니다.");
    
    private final HttpStatus status;
    private final String code;
    private final String message;
}
