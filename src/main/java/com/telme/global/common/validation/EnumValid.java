package com.telme.global.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = EnumValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface EnumValid {

    Class<? extends Enum<?>> enumClass();

    String message() default "허용되지 않는 값입니다.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
