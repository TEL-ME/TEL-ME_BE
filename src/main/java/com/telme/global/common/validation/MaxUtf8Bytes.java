package com.telme.global.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 문자 수(@Size)가 아니라 UTF-8 인코딩 바이트 수로 상한을 검사한다. BCrypt처럼 바이트 기준 제한이 있는 값에 쓴다. */
@Documented
@Constraint(validatedBy = MaxUtf8BytesValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface MaxUtf8Bytes {

    int value();

    String message() default "값이 최대 바이트 수를 초과했습니다.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
