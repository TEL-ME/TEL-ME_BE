package com.telme.notification.exception;

import com.telme.global.common.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum NotificationErrorCode implements BaseErrorCode {
    // 없음·남의 것·대상 아님을 구분하지 않고 같은 응답
    TARGET_UNAVAILABLE(HttpStatus.NOT_FOUND, "NOTIFICATION404-0", "알림을 저장할 수 있는 메시지를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
