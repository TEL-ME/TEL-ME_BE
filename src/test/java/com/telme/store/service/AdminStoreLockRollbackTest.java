package com.telme.store.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.telme.global.common.exception.GeneralException;
import com.telme.store.dto.req.AdminStoreSaveRequest;
import com.telme.store.entity.StoreServiceType;
import com.telme.store.exception.StoreErrorCode;
import com.telme.store.repository.StoreRepository;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
public class AdminStoreLockRollbackTest {

    private static final Long ADMIN_ID = 1L;
    
    @Autowired private AdminStoreCommandService commandService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private StoreRepository storeRepository;
    @Autowired private TransactionTemplate transactionTemplate;
    
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
    
    @Test
    @DisplayName("매장을 읽은 뒤 다른 관리자가 삭제하면 수정은 409로 막히고 폐점이 유지된다")
    void 수정_도중_삭제되면_폐점이_유지된다() {
        storeId = commandService.create(request("관리자쓰기 수정중삭제", LocalTime.of(10, 0), null), ADMIN_ID).storeId();

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            // 수정 요청이 OPEN·잠금 번호 0을 읽어 둔 시점에, 다른 관리자의 삭제가 먼저 커밋된다
            storeRepository.findById(storeId).orElseThrow();
            CompletableFuture.runAsync(() -> commandService.delete(storeId, ADMIN_ID)).join();
            commandService.update(storeId, request("삭제 뒤 저장", LocalTime.of(8, 0), 0), ADMIN_ID);
        }))
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getErrorCode())
                .isEqualTo(StoreErrorCode.CONCURRENT_UPDATE);

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM stores WHERE store_id = ?", String.class, storeId))
                .isEqualTo("CLOSED_DOWN");
        assertThat(jdbcTemplate.queryForObject("SELECT name FROM stores WHERE store_id = ?", String.class, storeId))
                .isEqualTo("관리자쓰기 수정중삭제");
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
