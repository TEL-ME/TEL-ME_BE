package com.telme.store.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.telme.global.common.exception.GeneralException;
import com.telme.store.dto.req.AdminStoreSaveRequest;
import com.telme.store.dto.res.AdminStoreDetailResponse;
import com.telme.store.dto.res.AdminStoreServiceResponse;
import com.telme.store.entity.Store;
import com.telme.store.entity.StoreServiceType;
import com.telme.store.exception.StoreErrorCode;
import com.telme.store.repository.StoreRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class AdminStoreCommandServiceTest {

    private static final Long ADMIN_ID = 1L;
    // V2 시드의 1번 매장. 테스트 전에 저장돼 있어 수정 시각 변화를 확인할 수 있다
    private static final Long SEED_STORE_ID = 1L;
    
    @Autowired private AdminStoreCommandService commandService;
    @Autowired private AdminStoreQueryService queryService;
    @Autowired private StoreRepository storeRepository;
    @Autowired private EntityManager entityManager;
    
    @Test
    @DisplayName("등록하면 세 묶음과 법정동코드가 저장되고 OPEN으로 시작하며 DB 시각이 담긴다")
    void 등록하면_세_묶음이_저장된다() {
        AdminStoreDetailResponse created = commandService.create(request("관리자쓰기 강남점", LocalTime.of(10, 0), 
                StoreServiceType.Code.USIM_REISSUE, StoreServiceType.Code.NEW_LINE), ADMIN_ID);
        
        assertThat(created.status()).isEqualTo("OPEN");
        assertThat(created.regionCode()).isEqualTo("1168010100");
        assertThat(created.createdAt()).isNotNull();
        assertThat(created.updatedAt()).isNotNull();
        assertThat(created.hours()).extracting(AdminStoreDetailResponse.Hours::dayOfWeek)
                                   .containsExactly("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY");
        assertThat(created.services()).extracting(AdminStoreServiceResponse::code).containsExactly("NEW_LINE", "USIM_REISSUE");
    }
    
    @Test
    @DisplayName("DB에 저장된 요일은 1(월)~7(일)이다")
    void 요일은_1부터_7로_저장된다() {
        Long storeId = commandService.create(request("관리자 쓰기 요일", LocalTime.of(10, 0), StoreServiceType.Code.NEW_LINE), ADMIN_ID).storeId();
        entityManager.flush();
        
        assertThat(dayOfWeekRows(storeId)).containsExactly(1, 2, 3, 4, 5, 6, 7);
    }
    
    @Test
    @DisplayName("수정하면 바꾼 요일과 업무만 반영되고 영업시간은 7행을 유지한다")
    void 수정은_요일별로_바꾸고_업무는_차이만_반영한다() {
        Long storeId = commandService.create(request("관리자쓰기 수정", LocalTime.of(10, 0), 
                StoreServiceType.Code.NEW_LINE, StoreServiceType.Code.USIM_REISSUE), ADMIN_ID).storeId();
        entityManager.flush();
        entityManager.clear();
        
        AdminStoreDetailResponse updated = commandService.update(storeId, 
                request("관리자쓰기 수정됨", LocalTime.of(9, 0), StoreServiceType.Code.USIM_REISSUE, StoreServiceType.Code.PORT_IN), ADMIN_ID);
        
        assertThat(updated.name()).isEqualTo("관리자쓰기 수정됨");
        assertThat(updated.hours().getFirst().openTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(updated.services()).extracting(AdminStoreServiceResponse::code).containsExactly("PORT_IN", "USIM_REISSUE");
        assertThat(dayOfWeekRows(storeId)).hasSize(7);
        assertThat(count("SELECT count(*) FROM store_services WHERE store_id = :id", storeId)).isEqualTo(2);
    }
    
    @Test
    @DisplayName("영업시간만 바꿔도 수정 시각이 오른다")
    void 영업시간만_바꿔도_수정_시각이_오른다() {
        Instant before = queryService.getStore(SEED_STORE_ID).updatedAt();
        Store seed = storeRepository.findById(SEED_STORE_ID).orElseThrow();
        AdminStoreSaveRequest sameBasicInfo = new AdminStoreSaveRequest(
                seed.getName(), seed.getAddress(), seed.getPhone(),
                seed.getRegionCode(), seed.getLatitude(), seed.getLongitude(), week(LocalTime.of(8, 0)),
                seed.getServices().stream().map(s -> s.getServiceType().getCode()).toList());
        entityManager.clear();
        
        AdminStoreDetailResponse updated = commandService.update(SEED_STORE_ID, sameBasicInfo, ADMIN_ID);
        
        assertThat(updated.updatedAt()).isAfter(before);
    }
    
    @Test
    @DisplayName("영업시간이 빠진 매장을 수정하면 빠진 요일이 채워진다")
    void 빠진_요일은_수정할_때_채워진다() {
        Long storeId = commandService.create(request("관리자쓰기 빠진요일", LocalTime.of(10, 0), StoreServiceType.Code.NEW_LINE), ADMIN_ID).storeId();
        entityManager.flush();
        entityManager.createQuery("delete from StoreHours h where h.id.storeId = :id and h.id.dayOfWeek = 7").setParameter("id", storeId).executeUpdate();
        entityManager.clear();
        
        commandService.update(storeId, request("관리자쓰기 빠진요일", LocalTime.of(10, 0), StoreServiceType.Code.NEW_LINE), ADMIN_ID);
        
        assertThat(dayOfWeekRows(storeId)).containsExactly(1, 2, 3, 4, 5, 6, 7);
    }
    
    @Test
    @DisplayName("삭제하면 CLOSED_DOWN이 되고 두 번 삭제해도 예외가 없다")
    void 삭제는_폐점으로_바꾼다() {
        Long storeId = commandService.create(request("관리자쓰기 삭제", LocalTime.of(10, 0), StoreServiceType.Code.NEW_LINE), ADMIN_ID).storeId();
        
        commandService.delete(storeId, ADMIN_ID);
        commandService.delete(storeId, ADMIN_ID);
        entityManager.flush();
        entityManager.clear();
        
        assertThat(queryService.getStore(storeId).status()).isEqualTo("CLOSED_DOWN");
    }
    
    @Test
    @DisplayName("없는 매장을 수정하거나 삭제하면 STORE404-0을 던진다")
    void 없는_매장은_예외를_던진다() {
        assertThatThrownBy(() -> commandService.update(999_999L, request("없음", LocalTime.of(10, 0), StoreServiceType.Code.NEW_LINE), ADMIN_ID))
                            .isInstanceOf(GeneralException.class)
                            .extracting(e -> ((GeneralException) e).getErrorCode())
                            .isEqualTo(StoreErrorCode.STORE_NOT_FOUND);
        
        assertThatThrownBy(() -> commandService.delete(999_999L, ADMIN_ID)).isInstanceOf(GeneralException.class);
    }
    
    private AdminStoreSaveRequest request(String name, LocalTime weekdayOpen, StoreServiceType.Code...codes) {
        return new AdminStoreSaveRequest(name, "서울특별시 강남구 테헤란로 123", null, "1168010100",  new BigDecimal("37.498095"), 
                                            new BigDecimal("127.027610"), week(weekdayOpen), Arrays.asList(codes));
    }
    
    // 월~토는 weekdayOpen~19:00, 일요일은 휴무
    private List<AdminStoreSaveRequest.Hours> week(LocalTime weekdayOpen) {
        return Arrays.stream(DayOfWeek.values())
                .map(day -> day == DayOfWeek.SUNDAY 
                        ? new AdminStoreSaveRequest.Hours(day, null, null, true) 
                        : new AdminStoreSaveRequest.Hours(day, weekdayOpen, LocalTime.of(19, 0), false)).toList();
    }
    
    private List<Integer> dayOfWeekRows(Long storeId) {
        return ((List<?>) entityManager.createNativeQuery("SELECT day_of_week FROM store_hours WHERE store_id = :id ORDER BY day_of_week")
                .setParameter("id", storeId)
                .getResultList()).stream()
                .map(v -> ((Number) v).intValue())
                .toList();
    }
    
    private long count(String sql, Long storeId) {
        return ((Number) entityManager.createNativeQuery(sql).setParameter("id", storeId).getSingleResult()).longValue();
    }
}
