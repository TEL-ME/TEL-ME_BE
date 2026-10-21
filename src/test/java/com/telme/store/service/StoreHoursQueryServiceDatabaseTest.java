package com.telme.store.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

// 시드 매장과 섞이지 않게 이 테스트가 만든 매장만 읽고, 롤백으로 흔적을 남기지 않는다
@SpringBootTest
@Transactional
class StoreHoursQueryServiceDatabaseTest {

    @Autowired
    private StoreHoursQueryService service;

    @Autowired
    private JdbcTemplate jdbc;

    private long insertStore(String name) {
        return jdbc.queryForObject("""
                INSERT INTO stores (name, address, latitude, longitude, status)
                VALUES (?, '주소', 36.0, 124.5, 'OPEN') RETURNING store_id
                """, Long.class, name);
    }

    @Test
    @DisplayName("여러 매장의 영업시간을 한 번에 매장별로 묶어 읽고 빠진 요일은 채우지 않는다")
    void 여러_매장을_묶어_읽는다() {
        long first = insertStore("영업시간 점검 1");
        long second = insertStore("영업시간 점검 2");
        long noHours = insertStore("영업시간 점검 3");
        jdbc.update("INSERT INTO store_hours VALUES (?, 6, '10:00', '17:00', false)", first);
        jdbc.update("INSERT INTO store_hours VALUES (?, 7, NULL, NULL, true)", first);
        jdbc.update("INSERT INTO store_hours VALUES (?, 1, '09:00', '20:00', false)", second);

        var result = service.hoursOf(List.of(first, second, noHours));

        assertThat(result).containsOnlyKeys(first, second);
        assertThat(result.get(first)).extracting("dayOfWeek").containsExactly("SATURDAY", "SUNDAY");
        assertThat(result.get(first).get(0).openTime()).hasToString("10:00");
        assertThat(result.get(first).get(1).closed()).isTrue();
        assertThat(result.get(second)).hasSize(1);
    }

    @Test
    void 매장이_없거나_목록이_비어_있으면_빈_결과다() {
        assertThat(service.hoursOf(List.of())).isEqualTo(Map.of());
        assertThat(service.hoursOf(null)).isEqualTo(Map.of());
        assertThat(service.hoursOf(List.of(-1L))).isEmpty();
    }
}
