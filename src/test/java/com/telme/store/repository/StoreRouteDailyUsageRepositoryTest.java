package com.telme.store.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.telme.store.dto.req.RouteMode;
import com.telme.store.service.StoreRouteDailyUsageService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// REQUIRES_NEW의 커밋·동시성을 실제 로컬 DB로 확인한다. 다른 테스트와 겹치지 않는 과거 날짜만 정리한다.
@SpringBootTest
class StoreRouteDailyUsageRepositoryTest {

    private static final LocalDate DAY = LocalDate.of(2001, 1, 1);
    @Autowired private StoreRouteDailyUsageService service;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from store_route_daily_usages where usage_date in (?, ?)", DAY, DAY.plusDays(1));
    }

    @Test
    void 날짜별_수단별_집계와_0예산() {
        assertThat(service.tryReserve(DAY, RouteMode.CAR, 0)).isFalse();
        assertThat(service.tryReserve(DAY, RouteMode.CAR, 1)).isTrue();
        assertThat(service.tryReserve(DAY, RouteMode.CAR, 1)).isFalse();
        assertThat(service.tryReserve(DAY, RouteMode.WALK, 1)).isTrue();
        assertThat(service.tryReserve(DAY.plusDays(1), RouteMode.CAR, 1)).isTrue();
        assertThat(used(DAY, RouteMode.CAR)).isEqualTo(1);
    }

    @Test
    void 외부_트랜잭션_롤백에도_집계_유지() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            assertThat(service.tryReserve(DAY, RouteMode.CAR, 1)).isTrue();
            status.setRollbackOnly();
        });
        assertThat(used(DAY, RouteMode.CAR)).isEqualTo(1);
        assertThat(service.tryReserve(DAY, RouteMode.CAR, 1)).isFalse();
    }

    @Test
    void 동시_첫_예약과_마지막_한자리도_초과하지_않음() throws Exception {
        for (int remaining : new int[] {5, 1}) {
            jdbc.update("delete from store_route_daily_usages where usage_date = ?", DAY);
            if (remaining == 1) {
                for (int i = 0; i < 4; i++) {
                    assertThat(service.tryReserve(DAY, RouteMode.CAR, 5)).isTrue();
                }
            }
            try (var executor = Executors.newFixedThreadPool(12)) {
                CountDownLatch start = new CountDownLatch(1);
                List<Future<Boolean>> results = new ArrayList<>();
                for (int i = 0; i < 12; i++) {
                    results.add(executor.submit(() -> {
                        start.await();
                        return service.tryReserve(DAY, RouteMode.CAR, 5);
                    }));
                }
                start.countDown();
                int accepted = 0;
                for (Future<Boolean> result : results) {
                    if (result.get(20, TimeUnit.SECONDS)) {
                        accepted++;
                    }
                }
                assertThat(accepted).isEqualTo(remaining);
                assertThat(used(DAY, RouteMode.CAR)).isEqualTo(5);
            }
        }
    }

    private Long used(LocalDate day, RouteMode mode) {
        return jdbc.queryForObject(
                "select used_amount from store_route_daily_usages where usage_date = ? and kind = ?",
                Long.class, day, mode.name());
    }
}
