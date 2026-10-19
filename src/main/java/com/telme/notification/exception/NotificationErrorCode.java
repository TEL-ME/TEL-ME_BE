package com.telme.notification.exception;

import com.telme.global.common.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum NotificationErrorCode implements BaseErrorCode {
    // 없는 메시지, 남의 메시지, 사용자 메시지·오류 응답·생성 중 답변을 구분하지 않고 같은 응답을 준다
    TARGET_UNAVAILABLE(HttpStatus.NOT_FOUND, "NOTIFICATION404-0", "알림을 저장할 수 있는 답변을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
