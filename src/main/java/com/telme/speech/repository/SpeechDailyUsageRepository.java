package com.telme.speech.repository;

import com.telme.speech.entity.SpeechDailyUsage;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpeechDailyUsageRepository extends JpaRepository<SpeechDailyUsage, Long> {

    // 읽고 비교하고 쓰는 세 단계로 나누면 동시 요청 둘이 같은 값을 읽고 둘 다 통과한다. 합이 한도 이하일 때만 갱신하는 한 문장으로 처리하고,
    // 같은 행의 동시 갱신은 DB 행 잠금이 차례로 세운다. 그날 첫 요청은 갱신 대상이 없어 INSERT가 한도와 무관하게 들어가므로 SELECT의 WHERE로 거른다.
    // 반환값이 1이면 차감됐고 0이면 한도 초과다
    @Modifying
    @Query(value = """
            insert into speech_daily_usages (usage_date, kind, used_amount)
            select cast(:day as date), cast(:kind as varchar), cast(:amount as bigint)
            where cast(:amount as bigint) <= cast(:dailyLimit as bigint)
            on conflict (usage_date, kind) do update
            set used_amount = speech_daily_usages.used_amount + excluded.used_amount,
                updated_at = now()
            where speech_daily_usages.used_amount + excluded.used_amount <= cast(:dailyLimit as bigint)
            """, nativeQuery = true)
    int chargeIfWithinLimit(
            @Param("day") LocalDate day,
            @Param("kind") String kind,
            @Param("amount") long amount,
            @Param("dailyLimit") long dailyLimit
    );
}
