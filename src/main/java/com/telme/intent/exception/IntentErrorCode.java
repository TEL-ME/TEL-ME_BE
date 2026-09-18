package com.telme.intent.exception;

import com.telme.global.common.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 의도 라우팅(Intent) 도메인 전용 에러코드.
 *
 * 팀 코딩 컨벤션 규칙 준수:
 *   - 위치: {domain}/exception/{Domain}ErrorCode.java
 *   - 포맷: {DOMAIN}{HTTP_STATUS}-{Seq} (상태별 0부터 시작)
 *   - 구현: BaseErrorCode 인터페이스 구현 enum
 */
@Getter
@AllArgsConstructor
public enum IntentErrorCode implements BaseErrorCode {

    ROUTING_NOT_FOUND(
        HttpStatus.NOT_FOUND, "INTENT404-0",
        "해당 메시지의 라우팅 결과를 찾을 수 없습니다."
    ),
    LLM_RESPONSE_PARSE_FAILED(
        HttpStatus.INTERNAL_SERVER_ERROR, "INTENT500-0",
        "LLM 응답 JSON 파싱에 실패했습니다."
    ),
    LLM_CONNECTION_FAILED(
        HttpStatus.SERVICE_UNAVAILABLE, "INTENT503-0",
        "LLM 서버에 연결할 수 없습니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}
