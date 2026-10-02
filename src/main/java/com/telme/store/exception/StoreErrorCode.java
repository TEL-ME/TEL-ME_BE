package com.telme.store.exception;

import com.telme.global.common.code.BaseErrorCode;

import lombok.AllArgsConstructor;
import lombok.Getter;

import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum StoreErrorCode implements BaseErrorCode{

    INVALID_COORDINATE(HttpStatus.BAD_REQUEST, "STORE400-0", "위치 좌표가 올바르지 않습니다."),
    INVALID_SEARCH_RADIUS(HttpStatus.BAD_REQUEST, "STORE400-1", "매장 검색 반경이 허용 범위를 벗어났습니다."),
    INVALID_SEARCH_LIMIT(HttpStatus.BAD_REQUEST, "STORE400-2", "매장 검색 개수가 허용 범위를 벗어났습니다."),
    OPEN_NOW_FILTER_DISABLED(HttpStatus.BAD_REQUEST, "STORE400-4", "영업 중 매장 검색은 아직 지원하지 않습니다."),
    STORE_NOT_FOUND(HttpStatus.NOT_FOUND, "STORE404-0", "매장을 찾을 수 없습니다."),
    SEARCH_TIMEOUT(HttpStatus.SERVICE_UNAVAILABLE, "STORE503-0",
            "매장 검색이 지연되고 있습니다. 검색 범위를 좁혀 다시 시도해 주세요.");
    
    private final HttpStatus status;
    private final String code;
    private final String message;
}
