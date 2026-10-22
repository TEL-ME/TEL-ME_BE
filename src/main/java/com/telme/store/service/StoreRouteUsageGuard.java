package com.telme.store.service;

import com.telme.global.common.exception.GeneralException;
import com.telme.store.config.StoreRouteUsageProperties;
import com.telme.store.dto.req.RouteMode;
import com.telme.store.exception.StoreErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;

@Component
@RequiredArgsConstructor
@Slf4j
public class StoreRouteUsageGuard {

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private final StoreRouteUsageProperties properties;
    private final StoreRouteDailyUsageService dailyUsageService;
    private final Clock clock;

    public void checkEnabled() {
        if (!properties.enabled()) {
            throw new GeneralException(StoreErrorCode.ROUTE_DISABLED);
        }
    }

    public void acquire(RouteMode mode) {
        checkEnabled();
        long limit = properties.dailyLimit(mode);
        if (limit == 0) {
            throw new GeneralException(StoreErrorCode.ROUTE_DAILY_LIMIT_EXCEEDED);
        }
        boolean reserved;
        try {
            reserved = dailyUsageService.tryReserve(LocalDate.ofInstant(clock.instant(), KOREA), mode, limit);
        } catch (DataAccessException | TransactionException e) {
            // 집계 불능 상태에서 외부 호출을 열면 비용 제한을 우회하므로 차단한다. 좌표는 기록하지 않는다.
            log.error("[KakaoRoute] 호출 예산 확인 실패 mode={} cause={}", mode, e.getClass().getSimpleName());
            throw new GeneralException(StoreErrorCode.ROUTE_UNAVAILABLE);
        }
        if (!reserved) {
            throw new GeneralException(StoreErrorCode.ROUTE_DAILY_LIMIT_EXCEEDED);
        }
    }
}
