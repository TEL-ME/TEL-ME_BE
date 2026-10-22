package com.telme.store.repository;

import com.telme.store.entity.StoreRouteDailyUsage;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoreRouteDailyUsageRepository extends JpaRepository<StoreRouteDailyUsage, Long> {

    // 최초 INSERT와 동시 UPDATE 모두 한도 안에서만 1회를 예약한다. 0이면 예산 소진이다.
    @Modifying
    @Query(value = """
            insert into store_route_daily_usages (usage_date, kind, used_amount)
            select cast(:day as date), cast(:kind as varchar), 1
            where cast(:dailyLimit as bigint) > 0
            on conflict (usage_date, kind) do update
            set used_amount = store_route_daily_usages.used_amount + 1,
                updated_at = now()
            where store_route_daily_usages.used_amount < cast(:dailyLimit as bigint)
            """, nativeQuery = true)
    int reserveIfWithinLimit(@Param("day") LocalDate day, @Param("kind") String kind,
                             @Param("dailyLimit") long dailyLimit);
}
