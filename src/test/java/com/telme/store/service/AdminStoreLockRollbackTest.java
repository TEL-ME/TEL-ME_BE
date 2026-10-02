package com.telme.store.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.telme.global.common.exception.GeneralException;
import com.telme.store.dto.req.AdminStoreSaveRequest;
import com.telme.store.entity.StoreServiceType;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
public class AdminStoreLockRollbackTest {

    private static final Long ADMIN_ID = 1L;
    
    @Autowired private AdminStoreCommandService commandService;
    @Autowired private JdbcTemplate jdbcTemplate;
    
    private Long storeId;
    
    @AfterEach
    void tearDown() {
        if (storeId != null) {
            jdbcTemplate.update("DELETE FROM stores WHERE store_id = ?", storeId);
        }
    }
    
    @Test
    @DisplayName("잠금 번호가 달라 409가 나면 매장 정보와 영업시간 변경이 DB에 남지 않는다")
    void 충돌하면_앞서_반영한_변경도_취소된다() {
        storeId = commandService.create(request("관리자쓰기 롤백", LocalTime.of(10, 0), null), ADMIN_ID).storeId();
        commandService.update(storeId, request("다른 관리자가 저장", LocalTime.of(10, 0), 0), ADMIN_ID);
        
        assertThatThrownBy(() -> commandService.update(storeId, 
                                                        request("낡은 화면에서 저장", LocalTime.of(8, 0), 0), ADMIN_ID))
                         .isInstanceOf(GeneralException.class);
        assertThat(jdbcTemplate.queryForObject("SELECT name FROM stores WHERE store_id = ?", 
                                                String.class, storeId))
                    .isEqualTo("다른 관리자가 저장");
        assertThat(jdbcTemplate.queryForObject("SELECT open_time FROM store_hours WHERE store_id = ? AND day_of_week = 1", 
                                                LocalTime.class, storeId))
                    .isEqualTo(LocalTime.of(10, 0));
        assertThat(jdbcTemplate.queryForObject("SELECT lock_version FROM stores WHERE store_id = ?", 
                                                Integer.class, storeId))
                    .isEqualTo(1);
        }
    
    private AdminStoreSaveRequest request(String name, LocalTime weekdayOpen, Integer lockVersion) {
        List<AdminStoreSaveRequest.Hours> week = Arrays.stream(DayOfWeek.values())
                .map(day -> day == DayOfWeek.SUNDAY
                            ? new AdminStoreSaveRequest.Hours(day, null, null, true)
                            : new AdminStoreSaveRequest.Hours(day, weekdayOpen, LocalTime.of(19, 0), false)).toList();
        return new AdminStoreSaveRequest(name, "서울특별시 강남구 테헤란로 123", null, "1168010100",
                                         new BigDecimal("37.498095"), new BigDecimal("127.027610"), week,
                                         List.of(StoreServiceType.Code.NEW_LINE), lockVersion);
    }
}
