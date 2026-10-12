package com.telme.subscription.exception;

import com.telme.global.common.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum SubscriptionErrorCode implements BaseErrorCode {

    BIRTH_DATE_REQUIRED(HttpStatus.BAD_REQUEST, "SUBSCRIPTION400-0", "청소년·시니어 요금제는 생년월일을 입력해 주세요."),
    INVALID_BIRTH_DATE(HttpStatus.BAD_REQUEST, "SUBSCRIPTION400-1", "생년월일이 올바르지 않습니다."),
    AGE_NOT_ELIGIBLE(HttpStatus.BAD_REQUEST, "SUBSCRIPTION400-2", "가입 대상 연령이 아닙니다."),
    SUBSCRIPTION_NOT_FOUND(HttpStatus.NOT_FOUND, "SUBSCRIPTION404-0", "가입한 요금제가 없습니다."),
    ALREADY_SUBSCRIBED(HttpStatus.CONFLICT, "SUBSCRIPTION409-0", "이미 가입한 요금제가 있습니다."),
    SAME_PLAN(HttpStatus.CONFLICT, "SUBSCRIPTION409-1", "이미 가입한 요금제입니다."),
    CHANGE_NOT_ALLOWED_IN_SUBSCRIBED_MONTH(HttpStatus.CONFLICT, "SUBSCRIPTION409-2",
            "가입한 달에는 요금제를 변경할 수 없습니다. 다음 달부터 변경할 수 있습니다."),
    MONTHLY_CHANGE_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "SUBSCRIPTION409-3", "요금제는 매월 1회만 변경할 수 있습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
