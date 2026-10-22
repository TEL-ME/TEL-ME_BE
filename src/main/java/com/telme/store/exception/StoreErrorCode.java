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
    ROUTE_DISTANCE_EXCEEDED(HttpStatus.BAD_REQUEST, "STORE400-5", "설정된 최대 이동 거리를 초과한 경로입니다."),
    STORE_NOT_FOUND(HttpStatus.NOT_FOUND, "STORE404-0", "매장을 찾을 수 없습니다."),
    ROUTE_NOT_FOUND(HttpStatus.NOT_FOUND, "STORE404-1", "현재 위치에서 매장까지의 경로를 찾을 수 없습니다."),
    STORE_CLOSED(HttpStatus.CONFLICT, "STORE409-0", "폐점한 매장은 수정할 수 없습니다."),
    CONCURRENT_UPDATE(HttpStatus.CONFLICT, "STORE409-1", "다른 관리자가 먼저 수정했습니다. 다시 불러온 뒤 저장해주세요."),
    SEARCH_TIMEOUT(HttpStatus.SERVICE_UNAVAILABLE, "STORE503-0",
            "매장 검색이 지연되고 있습니다. 검색 범위를 좁혀 다시 시도해 주세요."),
    LOCATION_LOOKUP_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "STORE503-1",
            "위치를 찾는 서비스에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요."),
    ROUTE_DISABLED(HttpStatus.SERVICE_UNAVAILABLE, "STORE503-3",
            "길찾기를 잠시 이용할 수 없습니다. 카카오맵에서 확인해 주세요."),
    ROUTE_DAILY_LIMIT_EXCEEDED(HttpStatus.SERVICE_UNAVAILABLE, "STORE503-4",
            "오늘 이 이동 수단의 길찾기 제공량을 모두 사용했습니다. 카카오맵에서 확인해 주세요."),
    ROUTE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "STORE503-2",
            "길찾기 서비스에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.");
    
    private final HttpStatus status;
    private final String code;
    private final String message;
}
