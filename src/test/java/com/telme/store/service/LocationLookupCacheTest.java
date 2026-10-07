package com.telme.store.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.telme.global.common.exception.GeneralException;
import com.telme.store.exception.StoreErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LocationLookupCacheTest {

    private static final LocationLookupResult GANGNAM_STATION = new LocationLookupResult(
            "강남역 2호선", LocationLookupResult.Type.PLACE, 37.4979, 127.0276, null);

    private LocationLookupService locationLookupService;
    private LocationLookupCache cache;

    @BeforeEach
    void setUp() {
        locationLookupService = mock(LocationLookupService.class);
        cache = new LocationLookupCache(locationLookupService,
                Clock.fixed(Instant.parse("2026-10-06T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    @DisplayName("같은 검색어는 앞뒤·중복 공백이 달라도 카카오를 한 번만 부른다")
    void 같은_검색어는_한_번만_부른다() {
        when(locationLookupService.lookup("강남역 2번 출구")).thenReturn(Optional.of(GANGNAM_STATION));

        assertThat(cache.lookup("강남역 2번 출구")).contains(GANGNAM_STATION);
        assertThat(cache.lookup("  강남역   2번  출구 ")).contains(GANGNAM_STATION);

        verify(locationLookupService, times(1)).lookup("강남역 2번 출구");
    }

    @Test
    @DisplayName("못 찾은 결과도 저장해 같은 검색어로 다시 부르지 않는다")
    void 못_찾은_결과도_저장한다() {
        when(locationLookupService.lookup("없는동네")).thenReturn(Optional.empty());

        assertThat(cache.lookup("없는동네")).isEmpty();
        assertThat(cache.lookup("없는동네")).isEmpty();

        verify(locationLookupService, times(1)).lookup("없는동네");
    }

    @Test
    @DisplayName("호출 실패는 저장하지 않아 다음 요청에서 다시 부른다")
    void 실패는_저장하지_않는다() {
        when(locationLookupService.lookup("강남역"))
                .thenThrow(new GeneralException(StoreErrorCode.LOCATION_LOOKUP_UNAVAILABLE))
                .thenReturn(Optional.of(GANGNAM_STATION));

        assertThatThrownBy(() -> cache.lookup("강남역")).isInstanceOf(GeneralException.class);
        assertThat(cache.lookup("강남역")).contains(GANGNAM_STATION);

        verify(locationLookupService, times(2)).lookup("강남역");
    }

    @Test
    @DisplayName("빈 검색어는 카카오를 부르지 않는다")
    void 빈_검색어는_부르지_않는다() {
        assertThat(cache.lookup(null)).isEmpty();
        assertThat(cache.lookup("  ")).isEmpty();
        assertThat(cache.lookup(" {} ")).isEmpty();

        verifyNoInteractions(locationLookupService);
    }

    @Test
    @DisplayName("중괄호는 빼고 카카오를 부른다")
    void 중괄호는_빼고_부른다() {
        when(locationLookupService.lookup("역삼동 1")).thenReturn(Optional.of(GANGNAM_STATION));

        assertThat(cache.lookup("역삼동 {1}")).contains(GANGNAM_STATION);

        verify(locationLookupService).lookup("역삼동 1");
    }
}
