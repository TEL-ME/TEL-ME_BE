package com.telme.store.dto.req;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StoreRouteRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("대한민국 안의 좌표와 이동 수단이 있으면 통과한다")
    void 정상() {
        assertThat(validate(new StoreRouteRequest(RouteMode.CAR, 37.5663, 126.9779))).isEmpty();
        assertThat(validate(new StoreRouteRequest(RouteMode.WALK, 33.1, 126.2))).isEmpty(); // 제주 남쪽
        assertThat(validate(new StoreRouteRequest(RouteMode.BICYCLE, 37.24, 131.87))).isEmpty(); // 독도
    }

    @Test
    @DisplayName("이동 수단이나 좌표가 없으면 거부한다")
    void 필수값() {
        assertThat(validate(new StoreRouteRequest(null, 37.5, 127.0))).hasSize(1);
        assertThat(validate(new StoreRouteRequest(RouteMode.CAR, null, 127.0))).hasSize(1);
        assertThat(validate(new StoreRouteRequest(RouteMode.CAR, 37.5, null))).hasSize(1);
    }

    @Test
    @DisplayName("대한민국 밖 좌표는 거부한다 (유료 호출 한도를 엉뚱한 곳에 쓰지 않도록)")
    void 범위_밖() {
        assertThat(validate(new StoreRouteRequest(RouteMode.CAR, 35.68, 139.69))).hasSize(1); // 도쿄(경도)
        assertThat(validate(new StoreRouteRequest(RouteMode.CAR, 40.71, -74.0))).hasSize(2); // 뉴욕
        assertThat(validate(new StoreRouteRequest(RouteMode.CAR, 0.0, 0.0))).hasSize(2);
    }

    private Set<ConstraintViolation<StoreRouteRequest>> validate(StoreRouteRequest request) {
        return validator.validate(request);
    }
}
