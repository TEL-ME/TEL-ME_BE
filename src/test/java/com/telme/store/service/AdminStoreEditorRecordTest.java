package com.telme.store.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.telme.store.dto.req.AdminStoreSaveRequest;
import com.telme.store.dto.res.AdminStoreDetailResponse;
import com.telme.store.entity.StoreServiceType;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

// 개발 시드(dev-migration V2)의 1번·2번 사용자를 서로 다른 관리자로 쓴다
@SpringBootTest
@Transactional
class AdminStoreEditorRecordTest {

    private static final Long CREATOR_ID = 1L;
    private static final Long EDITOR_ID = 2L;

    @Autowired private AdminStoreCommandService commandService;
    @Autowired private AdminStoreQueryService queryService;
    @Autowired private EntityManager entityManager;

    @Test
    @DisplayName("등록하면 생성자와 수정자가 모두 등록한 관리자로 남는다")
    void 등록() {
        AdminStoreDetailResponse created = commandService.create(request("기록 등록점", 0), CREATOR_ID);

        assertThat(created.createdBy()).isEqualTo(CREATOR_ID);
        assertThat(created.updatedBy()).isEqualTo(CREATOR_ID);
    }

    @Test
    @DisplayName("다른 관리자가 수정하면 수정자만 바뀌고 생성자는 그대로다")
    void 수정() {
        AdminStoreDetailResponse created = commandService.create(request("기록 수정점", 0), CREATOR_ID);

        AdminStoreDetailResponse updated = commandService.update(created.storeId(),
                request("기록 수정점 이름 변경", created.lockVersion()), EDITOR_ID);

        assertThat(updated.createdBy()).isEqualTo(CREATOR_ID);
        assertThat(updated.updatedBy()).isEqualTo(EDITOR_ID);
    }

    @Test
    @DisplayName("영업시간만 바꿔 매장 기본 정보가 그대로여도 수정자는 바뀐다")
    void 영업시간만_수정() {
        AdminStoreDetailResponse created = commandService.create(request("기록 영업시간점", 0), CREATOR_ID);
        AdminStoreSaveRequest onlyHours = new AdminStoreSaveRequest("기록 영업시간점", "서울특별시 강남구 테헤란로 123", null,
                "1168010100", new BigDecimal("37.498095"), new BigDecimal("127.027610"), week(LocalTime.of(9, 0)),
                List.of(StoreServiceType.Code.NEW_LINE), created.lockVersion());

        AdminStoreDetailResponse updated = commandService.update(created.storeId(), onlyHours, EDITOR_ID);

        assertThat(updated.updatedBy()).isEqualTo(EDITOR_ID);
    }

    @Test
    @DisplayName("다른 관리자가 폐점하면 수정자가 폐점한 관리자로 바뀐다")
    void 폐점() {
        Long storeId = commandService.create(request("기록 폐점점", 0), CREATOR_ID).storeId();

        commandService.delete(storeId, EDITOR_ID);
        entityManager.clear();

        AdminStoreDetailResponse closed = queryService.getStore(storeId);
        assertThat(closed.status()).isEqualTo("CLOSED_DOWN");
        assertThat(closed.createdBy()).isEqualTo(CREATOR_ID);
        assertThat(closed.updatedBy()).isEqualTo(EDITOR_ID);
    }

    private AdminStoreSaveRequest request(String name, Integer lockVersion) {
        return new AdminStoreSaveRequest(name, "서울특별시 강남구 테헤란로 123", null, "1168010100",
                new BigDecimal("37.498095"), new BigDecimal("127.027610"), week(LocalTime.of(10, 0)),
                List.of(StoreServiceType.Code.NEW_LINE), lockVersion);
    }

    private List<AdminStoreSaveRequest.Hours> week(LocalTime weekdayOpen) {
        return Arrays.stream(DayOfWeek.values())
                .map(day -> day == DayOfWeek.SUNDAY
                        ? new AdminStoreSaveRequest.Hours(day, null, null, true)
                        : new AdminStoreSaveRequest.Hours(day, weekdayOpen, LocalTime.of(19, 0), false))
                .toList();
    }
}
