package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.telme.chat.converter.ChatStoreConverter;
import com.telme.chat.dto.res.ChatStoreResponse;
import com.telme.chat.dto.res.ChatStoreSearchContextResponse;
import com.telme.consult.service.NamedLocationStoreSearchPort.SearchResult;
import com.telme.consult.service.NamedLocationStoreSearchPort.Status;
import com.telme.global.common.exception.GeneralException;
import com.telme.store.config.StoreSearchProperties;
import com.telme.store.dto.req.StoreNearbySearchRequest;
import com.telme.store.dto.req.StoreRegionSearchRequest;
import com.telme.store.dto.res.StoreNearbyResponse;
import com.telme.store.dto.res.StoreNearbySearchResponse;
import com.telme.store.dto.res.StoreRegionSearchResponse;
import com.telme.store.dto.res.StoreServiceTypeResponse;
import com.telme.store.entity.StoreServiceType;
import com.telme.store.exception.StoreErrorCode;
import com.telme.store.service.LocationLookupCache;
import com.telme.store.service.LocationLookupResult;
import com.telme.store.service.StoreRegionSearchService;
import com.telme.store.service.StoreSearchService;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NamedLocationStoreSearchAdapterTest {

    private static final LocationLookupResult GANGNAM_GU = new LocationLookupResult(
            "서울 강남구", LocationLookupResult.Type.REGION, 37.5172, 127.0473, "1168");
    private static final LocationLookupResult GANGNAM_STATION = new LocationLookupResult(
            "강남역 2호선", LocationLookupResult.Type.PLACE, 37.4979, 127.0276, null);
    private static final LocationLookupResult TEHERAN_152 = new LocationLookupResult(
            "서울 강남구 테헤란로 152", LocationLookupResult.Type.ADDRESS, 37.5000, 127.0364, null);

    private LocationLookupCache locationLookupCache;
    private StoreRegionSearchService storeRegionSearchService;
    private StoreSearchService storeSearchService;
    private NamedLocationStoreSearchAdapter adapter;

    @BeforeEach
    void setUp() {
        locationLookupCache = mock(LocationLookupCache.class);
        storeRegionSearchService = mock(StoreRegionSearchService.class);
        storeSearchService = mock(StoreSearchService.class);
        adapter = new NamedLocationStoreSearchAdapter(locationLookupCache, storeRegionSearchService,
                storeSearchService, new StoreSearchProperties(10_000, 10_000, 5, Duration.ofSeconds(2), false),
                new ChatStoreConverter());
    }

    @Test
    @DisplayName("행정구역이면 지역 코드로 기본 개수만큼 찾고, 거리 없이 지역 기준을 돌려준다")
    void 행정구역은_지역_검색() {
        when(locationLookupCache.lookup("강남구")).thenReturn(Optional.of(GANGNAM_GU));
        when(storeRegionSearchService.search(any())).thenReturn(regionResponse(regionStore(1L)));

        SearchResult result = adapter.search("강남구", Set.of(StoreServiceType.Code.USIM_REISSUE));

        ArgumentCaptor<StoreRegionSearchRequest> request = ArgumentCaptor.forClass(StoreRegionSearchRequest.class);
        verify(storeRegionSearchService).search(request.capture());
        assertThat(request.getValue().region()).isEqualTo("1168");
        assertThat(request.getValue().serviceType()).isEqualTo("USIM_REISSUE");
        assertThat(request.getValue().page()).isZero();
        assertThat(request.getValue().size()).isEqualTo(5);
        verifyNoInteractions(storeSearchService);

        assertThat(result.status()).isEqualTo(Status.SUCCESS);
        assertThat(result.stores()).extracting(ChatStoreResponse::storeId).containsExactly(1L);
        assertThat(result.stores().get(0).distanceMeters()).isNull();
        assertThat(result.context()).isEqualTo(
                new ChatStoreSearchContextResponse(ChatStoreSearchContextResponse.Type.REGION, "서울 강남구", null));
    }

    @Test
    @DisplayName("행정구역 안에 매장이 없으면 좌표 검색으로 넘어가지 않고 정상 0건으로 돌려준다")
    void 지역_0건은_정상_0건() {
        when(locationLookupCache.lookup("강남구")).thenReturn(Optional.of(GANGNAM_GU));
        when(storeRegionSearchService.search(any())).thenReturn(regionResponse());

        SearchResult result = adapter.search("강남구", Set.of());

        assertThat(result.status()).isEqualTo(Status.SUCCESS);
        assertThat(result.stores()).isEmpty();
        assertThat(result.context().type()).isEqualTo(ChatStoreSearchContextResponse.Type.REGION);
        verifyNoInteractions(storeSearchService);
    }

    @Test
    @DisplayName("장소 이름이면 그 좌표로 주변 매장을 찾고, 장소 기준과 실제 반경을 돌려준다")
    void 장소는_좌표_검색() {
        when(locationLookupCache.lookup("강남역")).thenReturn(Optional.of(GANGNAM_STATION));
        when(storeSearchService.findNearbyStores(any())).thenReturn(nearbyResponse(nearbyStore(3L, 120)));

        SearchResult result = adapter.search("강남역", Set.of(StoreServiceType.Code.PORT_IN));

        ArgumentCaptor<StoreNearbySearchRequest> request = ArgumentCaptor.forClass(StoreNearbySearchRequest.class);
        verify(storeSearchService).findNearbyStores(request.capture());
        assertThat(request.getValue().latitude()).isEqualTo(37.4979);
        assertThat(request.getValue().longitude()).isEqualTo(127.0276);
        assertThat(request.getValue().serviceTypes()).containsExactly(StoreServiceType.Code.PORT_IN);
        verifyNoInteractions(storeRegionSearchService);

        assertThat(result.status()).isEqualTo(Status.SUCCESS);
        assertThat(result.stores().get(0).distanceMeters()).isEqualTo(120);
        assertThat(result.context()).isEqualTo(
                new ChatStoreSearchContextResponse(ChatStoreSearchContextResponse.Type.PLACE, "강남역 2호선", 10_000));
    }

    @Test
    @DisplayName("주소면 좌표 검색을 하고 주소 기준으로 돌려준다")
    void 주소는_주소_기준() {
        when(locationLookupCache.lookup("테헤란로 152")).thenReturn(Optional.of(TEHERAN_152));
        when(storeSearchService.findNearbyStores(any())).thenReturn(nearbyResponse(nearbyStore(3L, 40)));

        SearchResult result = adapter.search("테헤란로 152", Set.of());

        assertThat(result.context().type()).isEqualTo(ChatStoreSearchContextResponse.Type.ADDRESS);
        assertThat(result.context().label()).isEqualTo("서울 강남구 테헤란로 152");
    }

    @Test
    @DisplayName("업무가 여러 개면 업무 조건을 지키려고 행정구역도 좌표 검색으로 찾는다")
    void 업무가_여러_개면_좌표_검색() {
        when(locationLookupCache.lookup("강남구")).thenReturn(Optional.of(GANGNAM_GU));
        when(storeSearchService.findNearbyStores(any())).thenReturn(nearbyResponse());
        Set<StoreServiceType.Code> codes = Set.of(StoreServiceType.Code.PORT_IN, StoreServiceType.Code.NEW_LINE);

        SearchResult result = adapter.search("강남구", codes);

        ArgumentCaptor<StoreNearbySearchRequest> request = ArgumentCaptor.forClass(StoreNearbySearchRequest.class);
        verify(storeSearchService).findNearbyStores(request.capture());
        assertThat(request.getValue().serviceTypes()).isEqualTo(codes);
        verifyNoInteractions(storeRegionSearchService);
        assertThat(result.context().type()).isEqualTo(ChatStoreSearchContextResponse.Type.PLACE);
    }

    @Test
    @DisplayName("위치를 찾지 못하면 매장 검색 없이 LOCATION_NOT_FOUND")
    void 위치를_못_찾으면_LOCATION_NOT_FOUND() {
        when(locationLookupCache.lookup("없는동네")).thenReturn(Optional.empty());

        SearchResult result = adapter.search("없는동네", Set.of());

        assertThat(result.status()).isEqualTo(Status.LOCATION_NOT_FOUND);
        assertThat(result.context()).isNull();
        verifyNoInteractions(storeRegionSearchService, storeSearchService);
    }

    @Test
    @DisplayName("카카오 결과에 이름이 없으면 사용자가 말한 위치를 기준 이름으로 쓴다")
    void 이름이_없으면_입력을_기준_이름으로() {
        LocationLookupResult unnamed = new LocationLookupResult(
                null, LocationLookupResult.Type.PLACE, 37.4979, 127.0276, null);
        when(locationLookupCache.lookup(" 강남역 ")).thenReturn(Optional.of(unnamed));
        when(storeSearchService.findNearbyStores(any())).thenReturn(nearbyResponse());

        assertThat(adapter.search(" 강남역 ", Set.of()).context().label()).isEqualTo("강남역");
    }

    @Test
    @DisplayName("카카오 장애와 검색 시간 초과는 잡지 않고 공통 처리기로 넘긴다")
    void 장애는_공통_처리기로_넘긴다() {
        when(locationLookupCache.lookup("강남역"))
                .thenThrow(new GeneralException(StoreErrorCode.LOCATION_LOOKUP_UNAVAILABLE));
        assertThatThrownBy(() -> adapter.search("강남역", Set.of())).isInstanceOf(GeneralException.class);

        when(locationLookupCache.lookup("강남구")).thenReturn(Optional.of(GANGNAM_STATION));
        when(storeSearchService.findNearbyStores(any()))
                .thenThrow(new GeneralException(StoreErrorCode.SEARCH_TIMEOUT));
        assertThatThrownBy(() -> adapter.search("강남구", Set.of())).isInstanceOf(GeneralException.class);
    }

    private static StoreRegionSearchResponse regionResponse(StoreRegionSearchResponse.Store... stores) {
        return new StoreRegionSearchResponse(List.of(stores), 0, 5, stores.length, stores.length == 0 ? 0 : 1);
    }

    private static StoreRegionSearchResponse.Store regionStore(long storeId) {
        return new StoreRegionSearchResponse.Store(storeId, "텔미 " + storeId + "호점", "서울 강남구 테헤란로 " + storeId,
                "02-000-000" + storeId, "1168010100", new BigDecimal("37.500000"), new BigDecimal("127.030000"),
                List.of(new StoreServiceTypeResponse("USIM_REISSUE", "유심재발급")));
    }

    private static StoreNearbySearchResponse nearbyResponse(StoreNearbyResponse... stores) {
        return StoreNearbySearchResponse.builder().stores(List.of(stores)).radiusMeters(10_000).build();
    }

    private static StoreNearbyResponse nearbyStore(long storeId, int distanceMeters) {
        return StoreNearbyResponse.builder()
                .storeId(storeId)
                .name("텔미 " + storeId + "호점")
                .address("서울 서초구 강남대로 " + storeId)
                .phone("02-111-111" + storeId)
                .latitude(new BigDecimal("37.498000"))
                .longitude(new BigDecimal("127.028000"))
                .distanceMeters(distanceMeters)
                .build();
    }
}
