package com.telme.store.service;

import com.telme.store.dto.req.RouteMode;
import com.telme.store.repository.StoreRouteDailyUsageRepository;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StoreRouteDailyUsageService {

    private final StoreRouteDailyUsageRepository storeRouteDailyUsageRepository;

    // 외부 호출 실패나 호출자의 롤백에도 예약을 유지하고, HTTP 대기 전에 DB 잠금을 해제한다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryReserve(LocalDate day, RouteMode mode, long dailyLimit) {
        return storeRouteDailyUsageRepository.reserveIfWithinLimit(day, mode.name(), dailyLimit) > 0;
    }
}
