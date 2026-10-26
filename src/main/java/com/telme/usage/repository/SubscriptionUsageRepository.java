package com.telme.usage.repository;

import com.telme.usage.entity.SubscriptionUsage;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubscriptionUsageRepository extends JpaRepository<SubscriptionUsage, Long> {

    // fromMonth(1일) 이후의 달을 오래된 순서로 조회
    List<SubscriptionUsage> findBySubscriptionIdAndUsageMonthGreaterThanEqualOrderByUsageMonthAsc(
            Long subscriptionId, LocalDate fromMonth);

    default List<SubscriptionUsage> findFromMonth(Long subscriptionId, LocalDate fromMonth) {
        return findBySubscriptionIdAndUsageMonthGreaterThanEqualOrderByUsageMonthAsc(subscriptionId, fromMonth);
    }

    // 같은 달 생성이 동시에 와도 먼저 들어간 값을 유지한다. 새로 넣었으면 1, 이미 있으면 0
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO subscription_usages
                (subscription_id, usage_month, used_data_mb, used_voice_minutes, used_sms_count)
            VALUES (:subscriptionId, :usageMonth, :usedDataMb, :usedVoiceMinutes, :usedSmsCount)
            ON CONFLICT (subscription_id, usage_month) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("subscriptionId") Long subscriptionId,
            @Param("usageMonth") LocalDate usageMonth,
            @Param("usedDataMb") long usedDataMb,
            @Param("usedVoiceMinutes") int usedVoiceMinutes,
            @Param("usedSmsCount") int usedSmsCount);
}
