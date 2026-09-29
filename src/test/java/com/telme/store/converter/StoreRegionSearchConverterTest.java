package com.telme.store.converter;

import static org.assertj.core.api.Assertions.assertThat;

import com.telme.store.dto.res.StoreRegionSearchResponse;
import com.telme.store.entity.Store;
import com.telme.store.entity.StoreService;
import com.telme.store.entity.StoreServiceType;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StoreRegionSearchConverterTest {

    private final StoreRegionSearchConverter converter = new StoreRegionSearchConverter();

    @Test
    @DisplayName("가능 업무를 코드 선언 순으로 정렬하고 매장 필드를 그대로 옮긴다")
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

        StoreRegionSearchResponse response = converter.toResponse(List.of(store));

        StoreRegionSearchResponse.Store card = response.stores().get(0);
        assertThat(card.storeId()).isEqualTo(101L);
        assertThat(card.regionCode()).isEqualTo("1168010700");
        assertThat(card.latitude()).isEqualByComparingTo("37.521450");
        assertThat(card.phone()).isNull();
        assertThat(card.services()).extracting(StoreRegionSearchResponse.ServiceType::code)
                .containsExactly("NEW_LINE", "USIM_REISSUE");
        assertThat(card.services().get(0).name()).isEqualTo("신규가입");
    }

    @Test
    @DisplayName("결과가 없으면 빈 목록")
    void 빈_결과() {
        assertThat(converter.toResponse(List.of()).stores()).isEmpty();
    }

    private StoreService service(Store store, StoreServiceType.Code code, String name) {
        StoreServiceType type = StoreServiceType.builder()
                .serviceTypeId((long) code.ordinal() + 1)
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
