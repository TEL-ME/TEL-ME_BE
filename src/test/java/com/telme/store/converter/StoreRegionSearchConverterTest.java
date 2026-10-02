package com.telme.store.converter;

import static org.assertj.core.api.Assertions.assertThat;

import com.telme.store.dto.res.StoreRegionSearchResponse;
import com.telme.store.dto.res.StoreServiceTypeResponse;
import com.telme.store.entity.Store;
import com.telme.store.entity.StoreService;
import com.telme.store.entity.StoreServiceType;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class StoreRegionSearchConverterTest {

    private final StoreRegionSearchConverter converter = new StoreRegionSearchConverter(new StoreCommonConverter());

    @Test
    @DisplayName("가능 업무를 업무 ID 순으로 정렬하고 매장 필드를 그대로 옮긴다")
    void 매장_카드_변환() {
        Store store = Store.builder()
                .storeId(101L)
                .name("텔미 강남구1호점")
                .address("서울특별시 강남구 도산대로15길 49")
                .regionCode("1168010700")
                .latitude(new BigDecimal("37.521450"))
                .longitude(new BigDecimal("127.023326"))
                .build();
        store.getServices().addAll(List.of(
                service(store, StoreServiceType.Code.USIM_REISSUE, "유심재발급"),
                service(store, StoreServiceType.Code.NEW_LINE, "신규가입")));

        StoreRegionSearchResponse response = converter.toResponse(
                new PageImpl<>(List.of(store), PageRequest.of(0, 20), 1), List.of(store));

        StoreRegionSearchResponse.Store card = response.stores().get(0);
        assertThat(card.storeId()).isEqualTo(101L);
        assertThat(card.regionCode()).isEqualTo("1168010700");
        assertThat(card.latitude()).isEqualByComparingTo("37.521450");
        assertThat(card.phone()).isNull();
        assertThat(card.services()).extracting(StoreServiceTypeResponse::code)
                .containsExactly("NEW_LINE", "USIM_REISSUE");
        assertThat(card.services().get(0).name()).isEqualTo("신규가입");
    }

    @Test
    @DisplayName("업무 순서는 enum 선언 순서가 아니라 관리자 API와 같은 업무 ID 순서를 따른다")
    void 업무_ID_순서() {
        Store store = Store.builder().storeId(101L).build();
        store.getServices().addAll(List.of(
                service(store, StoreServiceType.Code.NEW_LINE, "신규가입", 4L),
                service(store, StoreServiceType.Code.USIM_REISSUE, "유심재발급", 1L)));

        StoreRegionSearchResponse response = converter.toResponse(
                new PageImpl<>(List.of(store), PageRequest.of(0, 20), 1), List.of(store));

        assertThat(response.stores().get(0).services()).extracting(StoreServiceTypeResponse::code)
                .containsExactly("USIM_REISSUE", "NEW_LINE");
    }

    @Test
    @DisplayName("결과가 없으면 빈 목록")
    void 빈_결과() {
        StoreRegionSearchResponse response = converter.toResponse(Page.empty(PageRequest.of(0, 20)), List.of());

        assertThat(response.stores()).isEmpty();
        assertThat(response.totalElements()).isZero();
    }

    @Test
    @DisplayName("페이지 정보를 응답에 담는다")
    void 페이지_정보() {
        StoreRegionSearchResponse response = converter.toResponse(
                new PageImpl<>(List.of(), PageRequest.of(1, 10), 25), List.of());

        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(10);
        assertThat(response.totalElements()).isEqualTo(25);
        assertThat(response.totalPages()).isEqualTo(3);
    }

    private StoreService service(Store store, StoreServiceType.Code code, String name) {
        return service(store, code, name, (long) code.ordinal() + 1);
    }

    private StoreService service(Store store, StoreServiceType.Code code, String name, long serviceTypeId) {
        StoreServiceType type = StoreServiceType.builder()
                .serviceTypeId(serviceTypeId)
                .code(code)
                .name(name)
                .build();
        return StoreService.builder()
                .id(new StoreService.Id(store.getStoreId(), type.getServiceTypeId()))
                .store(store)
                .serviceType(type)
                .build();
    }
}
