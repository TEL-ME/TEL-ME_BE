package com.telme.store.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OpenNowConditionTest {

    @Test
    @DisplayName("요청 시각의 요일·전날 요일(1~7)과 시각을 파라미터로 넘긴다")
    void 파라미터() {
        OpenNowCondition condition = OpenNowCondition.at(LocalDateTime.of(2026, 9, 30, 15, 0));

        assertThat(condition.parameters())
                .containsEntry("openNowDay", 3)
                .containsEntry("openNowPreviousDay", 2)
                .containsEntry("openNowTime", LocalTime.of(15, 0));
    }

    @Test
    @DisplayName("월요일의 전날은 일요일(7)이다")
    void 월요일의_전날은_일요일() {
        assertThat(new OpenNowCondition(DayOfWeek.MONDAY, LocalTime.of(1, 0)).parameters())
                .containsEntry("openNowDay", 1)
                .containsEntry("openNowPreviousDay", 7);
    }

    @Test
    @DisplayName("시각마다 결과가 달라 공간 인덱스로 처리하는 조건이 아니다")
    void 공간_인덱스_조건이_아니다() {
        assertThat(new OpenNowCondition(DayOfWeek.MONDAY, LocalTime.NOON).backedBySpatialIndex()).isFalse();
    }

    @Test
    @DisplayName("요일이나 시각이 없으면 거부한다")
    void 요일_시각_필수() {
        assertThatThrownBy(() -> new OpenNowCondition(null, LocalTime.NOON))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OpenNowCondition(DayOfWeek.MONDAY, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
