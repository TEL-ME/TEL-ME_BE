package com.telme.store.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.telme.global.common.exception.GeneralException;
import com.telme.store.dto.req.AdminStoreSearchRequest;
import com.telme.store.dto.req.AdminStoreStatusFilter;
import com.telme.store.dto.res.AdminStoreDetailResponse;
import com.telme.store.dto.res.AdminStoreListItemResponse;
import com.telme.store.dto.res.AdminStoreListResponse;
import com.telme.store.dto.res.AdminStoreServiceResponse;
import com.telme.store.entity.Store;
import com.telme.store.entity.StoreHours;
import com.telme.store.entity.StoreService;
import com.telme.store.entity.StoreServiceType;
import com.telme.store.exception.StoreErrorCode;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class AdminStoreQueryServiceTest {

    // 시드 매장 150개와 섞이지 않도록 테스트 매장에만 들어가는 단어로 검색한다
    private static final String MARK = "관리자테스트";

    @Autowired
    private AdminStoreQueryService service;

    @Autowired
    private EntityManager entityManager;

    private Long gangnam;
    private Long busan;
    private Long closed;

    @BeforeEach
    void setUp() {
        gangnam = save(MARK + " 강남점", "서울특별시 강남구 " + MARK + "로 1", Store.Status.OPEN,
                StoreServiceType.Code.USIM_REISSUE, StoreServiceType.Code.NEW_LINE);
        busan = save(MARK + " 부산점", "부산광역시 해운대구 " + MARK + "로 2", Store.Status.OPEN,
                StoreServiceType.Code.PORT_IN);
        closed = save(MARK + " 폐점", "서울특별시 종로구 " + MARK + "로 3", Store.Status.CLOSED_DOWN,
                StoreServiceType.Code.NAME_CHANGE);
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("상태를 주지 않으면 운영 중인 매장만 조회한다")
    void 기본은_운영_중인_매장만_조회한다() {
        assertThat(ids(search(MARK, null))).containsExactlyInAnyOrder(gangnam, busan);
    }

    @Test
    @DisplayName("CLOSED_DOWN은 폐점 매장만, ALL은 전체를 조회한다")
    void 상태_필터로_폐점_매장을_조회한다() {
        assertThat(ids(search(MARK, AdminStoreStatusFilter.CLOSED_DOWN))).containsExactly(closed);
        assertThat(ids(search(MARK, AdminStoreStatusFilter.ALL))).containsExactlyInAnyOrder(gangnam, busan, closed);
    }

    @Test
    @DisplayName("검색어로 매장명과 주소를 찾고 앞뒤 공백은 무시한다")
    void 검색어로_매장명과_주소를_찾는다() {
        assertThat(ids(search(MARK + " 부산점", null))).containsExactly(busan);
        assertThat(ids(search("강남구 " + MARK, null))).containsExactly(gangnam);
        assertThat(ids(search("  " + MARK + " 부산점  ", null))).containsExactly(busan);
    }

    @Test
    @DisplayName("검색어의 %와 _는 와일드카드가 아니라 글자 그대로 찾는다")
    void 검색어의_와일드카드를_글자로_찾는다() {
        Long percent = save(MARK + " 10% 할인점", "서울특별시 중구 " + MARK + "로 4", Store.Status.OPEN,
                StoreServiceType.Code.NEW_LINE);
        entityManager.flush();
        entityManager.clear();

        // 이스케이프가 없으면 "%"는 전체, "_"는 아무 글자 하나와 일치한다
        assertThat(ids(search("%", null))).containsExactly(percent);
        assertThat(search("_", null).stores()).isEmpty();
    }

    @Test
    @DisplayName("목록은 취급 업무 이름을 id 순으로 담고 전체 개수는 필터 결과와 같다")
    void 목록에_취급_업무와_필터된_개수가_나온다() {
        AdminStoreListResponse response = search(MARK + " 강남점", null);

        AdminStoreListItemResponse item = response.stores().getFirst();
        assertThat(item.services()).extracting(AdminStoreServiceResponse::name)
                .containsExactly("신규가입", "유심재발급");
        assertThat(item.status()).isEqualTo("OPEN");
        assertThat(item.updatedAt()).isNotNull();
        assertThat(response.totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 조건의 다음 페이지에는 앞 페이지 매장이 다시 나오지 않는다")
    void 페이지_사이에_중복이_없다() {
        List<Long> first = ids(service.getStores(new AdminStoreSearchRequest(MARK, AdminStoreStatusFilter.ALL, 0, 2)));
        List<Long> second = ids(service.getStores(new AdminStoreSearchRequest(MARK, AdminStoreStatusFilter.ALL, 1, 2)));

        assertThat(first).hasSize(2).doesNotContainAnyElementsOf(second);
        assertThat(second).hasSize(1);
    }

    @Test
    @DisplayName("범위를 넘는 페이지는 빈 목록을 반환한다")
    void 범위_밖_페이지는_빈_목록이다() {
        AdminStoreListResponse response = service.getStores(new AdminStoreSearchRequest(MARK, null, 999, 20));

        assertThat(response.stores()).isEmpty();
        assertThat(response.totalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("상세는 월요일부터 7일 영업시간과 법정동코드를 반환한다")
    void 상세는_영업시간_7일과_법정동코드를_준다() {
        AdminStoreDetailResponse detail = service.getStore(gangnam);

        assertThat(detail.regionCode()).isEqualTo("1168010100");
        assertThat(detail.hours()).extracting(AdminStoreDetailResponse.Hours::dayOfWeek)
                .containsExactly("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY");
        assertThat(detail.hours().getLast().closed()).isTrue();
        assertThat(detail.hours().getLast().openTime()).isNull();
        assertThat(detail.hours().getFirst().openTime()).isEqualTo(LocalTime.of(10, 0));
    }

    @Test
    @DisplayName("폐점 매장도 상세 조회할 수 있다")
    void 폐점_매장도_상세_조회된다() {
        assertThat(service.getStore(closed).status()).isEqualTo("CLOSED_DOWN");
    }

    @Test
    @DisplayName("없는 매장은 STORE_NOT_FOUND 예외를 던진다")
    void 없는_매장은_예외를_던진다() {
        assertThatThrownBy(() -> service.getStore(Long.MAX_VALUE))
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getErrorCode())
                .isEqualTo(StoreErrorCode.STORE_NOT_FOUND);
    }

    private AdminStoreListResponse search(String keyword, AdminStoreStatusFilter status) {
        return service.getStores(new AdminStoreSearchRequest(keyword, status, null, null));
    }

    private List<Long> ids(AdminStoreListResponse response) {
        return response.stores().stream().map(AdminStoreListItemResponse::storeId).toList();
    }

    // 월~토 10:00~19:00 영업, 일요일 휴무. 업무는 시드의 store_service_types(1~4)를 쓴다
    private Long save(String name, String address, Store.Status status, StoreServiceType.Code... codes) {
        Store store = Store.builder()
                .name(name)
                .address(address)
                .regionCode("1168010100")
                .latitude(new BigDecimal("37.498095"))
                .longitude(new BigDecimal("127.027610"))
                .status(status)
                .build();
        for (short day = 1; day <= 7; day++) {
            boolean sunday = day == 7;
            store.getHours().add(StoreHours.builder()
                    .id(new StoreHours.Id(null, day))
                    .store(store)
                    .openTime(sunday ? null : LocalTime.of(10, 0))
                    .closeTime(sunday ? null : LocalTime.of(19, 0))
                    .closed(sunday)
                    .build());
        }
        for (StoreServiceType.Code code : codes) {
            StoreServiceType type = entityManager
                    .createQuery("select t from StoreServiceType t where t.code = :code", StoreServiceType.class)
                    .setParameter("code", code)
                    .getSingleResult();
            store.getServices().add(StoreService.builder()
                    .id(new StoreService.Id(null, type.getServiceTypeId()))
                    .store(store)
                    .serviceType(type)
                    .build());
        }
        entityManager.persist(store);
        return store.getStoreId();
    }
}