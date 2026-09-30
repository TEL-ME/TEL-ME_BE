package com.telme.global.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EnumValidatorTest {

    enum Color { RED, BLUE }

    record Target(@EnumValid(enumClass = Color.class, message = "허용되지 않는 색입니다.") String color) {
    }

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("enum 상수 이름과 정확히 같은 값만 통과한다")
    void 상수_이름은_통과() {
        assertThat(validator.validate(new Target("RED"))).isEmpty();
        assertThat(validator.validate(new Target("BLUE"))).isEmpty();
    }

    @Test
    @DisplayName("없는 값이나 대소문자가 다른 값은 지정한 메시지로 실패한다")
    void 없는_값은_실패() {
        for (String value : new String[] {"GREEN", "red", " RED", ""}) {
            Set<ConstraintViolation<Target>> violations = validator.validate(new Target(value));

            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getMessage()).isEqualTo("허용되지 않는 색입니다.");
        }
    }

    @Test
    @DisplayName("null은 검사하지 않는다 (필수 여부는 @NotNull이 담당)")
    void null은_통과() {
        assertThat(validator.validate(new Target(null))).isEmpty();
    }
}
