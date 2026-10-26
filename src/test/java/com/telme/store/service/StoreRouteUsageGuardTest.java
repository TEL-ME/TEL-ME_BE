package com.telme.store.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.telme.global.common.exception.GeneralException;
import com.telme.store.config.StoreRouteUsageProperties;
import com.telme.store.dto.req.RouteMode;
import com.telme.store.exception.StoreErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.CannotCreateTransactionException;

class StoreRouteUsageGuardTest {

    private final StoreRouteDailyUsageService service = mock(StoreRouteDailyUsageService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2001-01-01T15:00:00Z"), ZoneOffset.UTC);

    @ParameterizedTest
    @EnumSource(RouteMode.class)
    @DisplayName("스위치 OFF이면 DB 차감 전 모든 수단을 차단한다")
    void 기능_중단(RouteMode mode) {
        StoreRouteUsageGuard guard = guard(false, 10);
        assertError(() -> new KakaoRouteCalls(guard).call(mode, "test", () -> {
            throw new AssertionError("외부 호출 금지");
        }), StoreErrorCode.ROUTE_DISABLED);
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @EnumSource(RouteMode.class)
    @DisplayName("예산 0은 무제한이 아니라 해당 API 차단이다")
    void 예산_0(RouteMode mode) {
        assertError(() -> guard(true, 0).acquire(mode), StoreErrorCode.ROUTE_DAILY_LIMIT_EXCEEDED);
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("자정 경계는 서버 UTC가 아닌 KST 날짜를 사용한다")
    void 한국_날짜() {
        when(service.tryReserve(any(), any(), anyLong())).thenReturn(true);
        new StoreRouteUsageGuard(new StoreRouteUsageProperties(true, 90, 80, 70, 60), service, clock)
                .acquire(RouteMode.WALK);
        verify(service).tryReserve(LocalDate.of(2001, 1, 2), RouteMode.WALK, 70);
    }

    @Test
    @DisplayName("예약이 거절되면 일일 한도 전용 코드를 반환한다")
    void 일일_소진() {
        assertError(() -> new KakaoRouteCalls(guard(true, 10)).call(RouteMode.CAR, "test", () -> {
            throw new AssertionError("외부 호출 금지");
        }), StoreErrorCode.ROUTE_DAILY_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("DB 조회나 트랜잭션 시작이 실패해도 호출을 허용하지 않는다")
    void 집계_실패() {
        when(service.tryReserve(any(), any(), anyLong()))
                .thenThrow(new DataAccessResourceFailureException("db unavailable"))
                .thenThrow(new CannotCreateTransactionException("db unavailable"));
        assertError(() -> new KakaoRouteCalls(guard(true, 10)).call(RouteMode.CAR, "test", () -> {
            throw new AssertionError("외부 호출 금지");
        }), StoreErrorCode.ROUTE_UNAVAILABLE);
        assertError(() -> new KakaoRouteCalls(guard(true, 10)).call(RouteMode.CAR, "test", () -> {
            throw new AssertionError("외부 호출 금지");
        }), StoreErrorCode.ROUTE_UNAVAILABLE);
    }

    private StoreRouteUsageGuard guard(boolean enabled, long limit) {
        return new StoreRouteUsageGuard(new StoreRouteUsageProperties(enabled, limit, limit, limit, limit),
                service, clock);
    }

    private void assertError(Runnable action, StoreErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(GeneralException.class,
                e -> org.assertj.core.api.Assertions.assertThat(e.getErrorCode()).isEqualTo(code));
    }
}
