package com.telme.store.exception;

import com.telme.global.common.code.BaseErrorCode;

import lombok.AllArgsConstructor;
import lombok.Getter;

import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum StoreErrorCode implements BaseErrorCode{

    INVALID_COORDINATE(HttpStatus.BAD_REQUEST, "STORE400-0", "위도와 경도를 모두 입력해 주세요."),
    OPEN_NOW_FILTER_DISABLED(HttpStatus.BAD_REQUEST, "STORE400-4", "영업 중 매장 검색은 아직 지원하지 않습니다."),
    STORE_NOT_FOUND(HttpStatus.NOT_FOUND, "STORE404-0", "매장을 찾을 수 없습니다."),
    SEARCH_TIMEOUT(HttpStatus.SERVICE_UNAVAILABLE, "STORE503-0",
            "매장 검색이 지연되고 있습니다. 검색 범위를 좁혀 다시 시도해 주세요."),
    LOCATION_LOOKUP_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "STORE503-1",
            "위치를 찾는 서비스에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.");
    
    private final HttpStatus status;
    private final String code;
    private final String message;
}
