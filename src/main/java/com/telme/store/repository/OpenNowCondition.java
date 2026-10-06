package com.telme.store.repository;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;

// (동적조건)요청 시각에 영업 중인 매장만 남긴다. 시각마다 결과가 달라 공간 부분 인덱스로 만들 수 없다
public record OpenNowCondition(DayOfWeek dayOfWeek, LocalTime time) implements StoreSearchCondition {

    public OpenNowCondition {
        if (dayOfWeek == null || time == null) {
            throw new IllegalArgumentException("영업 중 조건에는 요일과 시각이 필요합니다.");
        }
    }

    public static OpenNowCondition at(LocalDateTime dateTime) {
        return new OpenNowCondition(dateTime.getDayOfWeek(), dateTime.toLocalTime());
    }

    @Override
    public String toSql() {
        return """
                EXISTS (SELECT 1 FROM store_hours h
                        WHERE h.store_id = s.store_id
                          AND h.day_of_week IN (:openNowDay, :openNowPreviousDay)
                          AND NOT h.is_closed
                          AND ((h.day_of_week = :openNowDay AND h.open_time <= :openNowTime
                                AND (h.close_time <= h.open_time OR :openNowTime < h.close_time))
                            OR (h.day_of_week = :openNowPreviousDay AND h.close_time <= h.open_time
                                AND :openNowTime < h.close_time)))""";
    }

    // store_hours.day_of_week 는 java.time.DayOfWeek 와 같은 1(월)~7(일)이다(V6)
    @Override
    public Map<String, Object> parameters() {
        return Map.of(
                "openNowDay", dayOfWeek.getValue(),
                "openNowPreviousDay", dayOfWeek.minus(1).getValue(),
                "openNowTime", time);
    }
}
