package com.telme.store.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telme.global.common.exception.GeneralException;
import com.telme.store.config.StoreSearchProperties;
import com.telme.store.converter.StoreConverter;
import com.telme.store.dto.req.StoreNearbySearchRequest;
import com.telme.store.dto.res.StoreNearbyResponse;
import com.telme.store.entity.StoreServiceType;
import com.telme.store.exception.StoreErrorCode;
import com.telme.store.repository.OpenNowCondition;
import com.telme.store.repository.StoreNearbyQueryRepository;
import com.telme.store.repository.StoreTag;
import com.telme.store.repository.StoreTagCondition;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;

class StoreSearchServiceTest {

    private static final double LATITUDE = 37.498095;
    private static final double LONGITUDE = 127.027610;
    // 2026-09-30(수) 15:00 한국 시각
    private static final Clock WEDNESDAY_3PM_KST = Clock.fixed(Instant.parse("2026-09-30T06:00:00Z"), ZoneOffset.UTC);

    private StoreNearbyQueryRepository repository;
    private StoreSearchService service;

    @BeforeEach
    void setUp() {
        repository = mock(StoreNearbyQueryRepository.class);
        service = new StoreSearchService(repository, new StoreConverter(), properties(false), WEDNESDAY_3PM_KST);
    }

    @Test
    @DisplayName("필터가 없으면 규모와 관계없이 KNN으로 찾고, 반경과 개수를 비우면 설정 기본값을 쓴다")
    void 기본값으로_검색한다() {
        service.findNearbyStores(request(LATITUDE, LONGITUDE, null, null));

        StoreNearbyQueryRepository.Query query = capturedQuery();
        assertThat(query.latitude()).isEqualTo(LATITUDE);
        assertThat(query.longitude()).isEqualTo(LONGITUDE);
        assertThat(query.radiusMeters()).isEqualTo(10000);
        assertThat(query.limit()).isEqualTo(5);
        assertThat(query.conditions()).isEmpty();
    }

    @Test
    @DisplayName("반경과 개수를 지정하면 그 값으로 검색한다")
    void 지정한_값으로_검색한다() {
        service.findNearbyStores(request(LATITUDE, LONGITUDE, 3000, 20));

        StoreNearbyQueryRepository.Query query = capturedQuery();
        assertThat(query.radiusMeters()).isEqualTo(3000);
        assertThat(query.limit()).isEqualTo(20);
    }

    @Test
    @DisplayName("업무 종류를 고르면 태그 조건 하나로 묶어 태그 공간 인덱스에서 KNN으로 찾는다")
    void 업무_조건을_붙인다() {
        Set<StoreServiceType.Code> serviceTypes =
                Set.of(StoreServiceType.Code.USIM_REISSUE, StoreServiceType.Code.PORT_IN);

        service.findNearbyStores(request(LATITUDE, LONGITUDE, null, null, serviceTypes));

        assertThat(capturedFilteredQuery().conditions())
                .containsExactly(StoreTagCondition.of(List.of(StoreTag.USIM_REISSUE, StoreTag.PORT_IN)));
    }

    @Test
    @DisplayName("업무 종류가 빈 목록이면 필터 없음으로 보고 KNN으로 찾는다")
    void 빈_업무_목록은_조건_없음() {
        service.findNearbyStores(request(LATITUDE, LONGITUDE, null, null, Set.of()));

        assertThat(capturedQuery().conditions()).isEmpty();
    }

    @Test
    @DisplayName("업무 종류에 null이 섞이면 거부한다")
    void 업무_종류_null은_거부한다() {
        Set<StoreServiceType.Code> serviceTypes = new HashSet<>();
        serviceTypes.add(StoreServiceType.Code.NEW_LINE);
        serviceTypes.add(null);

        assertErrorCode(request(LATITUDE, LONGITUDE, null, null, serviceTypes), StoreErrorCode.INVALID_SERVICE_TYPE);
    }

    @Test
    @DisplayName("영업 중 필터가 꺼져 있으면 조건을 빼지 않고 거부한다")
    void 꺼진_영업_중_필터는_거부한다() {
        assertErrorCode(openNowRequest(null, null), StoreErrorCode.OPEN_NOW_FILTER_DISABLED);
    }

    @Test
    @DisplayName("영업 중 필터가 false나 null이면 조건 없이 찾는다")
    void 영업_중_false는_조건_없음() {
        service.findNearbyStores(StoreNearbySearchRequest.builder()
                .latitude(LATITUDE).longitude(LONGITUDE).openNow(false).build());

        assertThat(capturedQuery().conditions()).isEmpty();
    }

    @Test
    @DisplayName("영업 중 필터: 서버 시계(UTC)를 한국 시각으로 바꿔 요일과 시각을 정한다")
    void 영업_중은_한국_시각_기준() {
        enabledService(WEDNESDAY_3PM_KST).findNearbyStores(openNowRequest(null, null));

        assertThat(capturedDynamicQuery().conditions())
                .containsExactly(new OpenNowCondition(DayOfWeek.WEDNESDAY, LocalTime.of(15, 0)));
    }

    @Test
    @DisplayName("영업 중 필터: UTC로는 화요일이어도 한국 시각이 수요일 새벽이면 수요일로 본다")
    void 영업_중은_한국_날짜_기준() {
        Clock tuesdayUtc = Clock.fixed(Instant.parse("2026-09-29T16:30:00Z"), ZoneOffset.UTC);

        enabledService(tuesdayUtc).findNearbyStores(openNowRequest(null, null));

        assertThat(capturedDynamicQuery().conditions())
                .containsExactly(new OpenNowCondition(DayOfWeek.WEDNESDAY, LocalTime.of(1, 30)));
    }

    @Test
    @DisplayName("영업 중 필터: 가까운 후보(요청 개수의 10배)에서 요청 개수를 채우면 반경 전체를 확인하지 않는다")
    void 후보로_채우면_끝낸다() {
        when(repository.findNearestCandidatesMatching(any(), anyInt()))
                .thenReturn(List.of(row(1L, 10), row(2L, 20)));

        List<StoreNearbyResponse> result = enabledService(WEDNESDAY_3PM_KST).findNearbyStores(openNowRequest(null, 2));

        assertThat(result).extracting(StoreNearbyResponse::storeId).containsExactly(1L, 2L);
        verify(repository).findNearestCandidatesMatching(any(), eq(20));
        verify(repository, never()).findMatchingWithinRadius(any());
    }

    @Test
    @DisplayName("영업 중 필터: 후보에서 요청 개수를 못 채우면 반경 안을 모두 확인한 결과를 반환한다")
    void 후보로_못_채우면_반경_전체를_확인한다() {
        when(repository.findNearestCandidatesMatching(any(), anyInt())).thenReturn(List.of(row(1L, 10)));
        when(repository.findMatchingWithinRadius(any())).thenReturn(List.of(row(1L, 10), row(3L, 900)));

        List<StoreNearbyResponse> result = enabledService(WEDNESDAY_3PM_KST).findNearbyStores(openNowRequest(null, 2));

        assertThat(result).extracting(StoreNearbyResponse::storeId).containsExactly(1L, 3L);
    }

    @Test
    @DisplayName("영업 중 필터와 업무 조건을 함께 고르면 두 조건을 모두 붙인다")
    void 영업_중과_업무_조건을_함께_붙인다() {
        Set<StoreServiceType.Code> usim = Set.of(StoreServiceType.Code.USIM_REISSUE);

        enabledService(WEDNESDAY_3PM_KST).findNearbyStores(openNowRequest(usim, null));

        assertThat(capturedDynamicQuery().conditions()).containsExactly(
                StoreTagCondition.of(List.of(StoreTag.USIM_REISSUE)),
                new OpenNowCondition(DayOfWeek.WEDNESDAY, LocalTime.of(15, 0)));
    }

    @Test
    @DisplayName("거리를 m 단위 정수로 반올림해 반환한다")
    void 거리를_반올림한다() {
        when(repository.findNearest(any())).thenReturn(List.of(row(1L, 1234.5), row(2L, 1234.4)));

        List<StoreNearbyResponse> result = service.findNearbyStores(request(LATITUDE, LONGITUDE, null, null));

        assertThat(result).extracting(StoreNearbyResponse::storeId).containsExactly(1L, 2L);
        assertThat(result).extracting(StoreNearbyResponse::distanceMeters).containsExactly(1235, 1234);
    }

    @Test
    @DisplayName("쿼리 타임아웃이면 검색 지연 오류로 바꾼다")
    void 쿼리_타임아웃은_검색_지연_오류() {
        when(repository.findNearest(any())).thenThrow(new QueryTimeoutException("timeout"));
        when(repository.findNearestMatching(any())).thenThrow(new DataAccessResourceFailureException(
                "canceled", new SQLException("canceling statement due to user request", "57014")));
        when(repository.findNearestCandidatesMatching(any(), anyInt())).thenReturn(List.of());
        when(repository.findMatchingWithinRadius(any())).thenThrow(new QueryTimeoutException("timeout"));

        assertSearchTimeout(request(LATITUDE, LONGITUDE, null, null));
        assertSearchTimeout(request(LATITUDE, LONGITUDE, null, null, Set.of(StoreServiceType.Code.USIM_REISSUE)));
        service = enabledService(WEDNESDAY_3PM_KST);
        assertSearchTimeout(openNowRequest(null, null));
    }

    @Test
    @DisplayName("타임아웃이 아닌 DB 오류는 그대로 올린다")
    void 다른_DB_오류는_그대로() {
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException(
                "connection", new SQLException("connection refused", "08001"));
        when(repository.findNearest(any())).thenThrow(failure);

        assertThatThrownBy(() -> service.findNearbyStores(request(LATITUDE, LONGITUDE, null, null)))
                .isSameAs(failure);
    }

    @ParameterizedTest(name = "위도 {0}, 경도 {1}")
    @CsvSource({
            "90.000001, 127.0",
            "-90.000001, 127.0",
            "37.5, 180.000001",
            "37.5, -180.000001",
            "NaN, 127.0",
            "37.5, Infinity"
    })
    @DisplayName("좌표가 범위를 벗어나면 검색하지 않고 거부한다")
    void 잘못된_좌표는_거부한다(double latitude, double longitude) {
        assertErrorCode(request(latitude, longitude, null, null), StoreErrorCode.INVALID_COORDINATE);
    }

    @Test
    @DisplayName("좌표가 없으면 거부한다")
    void 좌표가_없으면_거부한다() {
        assertErrorCode(request(null, LONGITUDE, null, null), StoreErrorCode.INVALID_COORDINATE);
        assertErrorCode(request(LATITUDE, null, null, null), StoreErrorCode.INVALID_COORDINATE);
    }

    @Test
    @DisplayName("지구 좌표 범위의 경계값은 오류가 아니다")
    void 좌표_경계값은_허용한다() {
        assertThat(service.findNearbyStores(request(90.0, 180.0, null, null))).isEmpty();
        assertThat(service.findNearbyStores(request(-90.0, -180.0, null, null))).isEmpty();
    }

    @ParameterizedTest(name = "위도 {0}, 경도 {1}")
    @CsvSource({"40.7128, -74.0060", "32.99, 126.5", "38.71, 127.0", "37.5, 124.49", "37.5, 132.01"})
    @DisplayName("국내 서비스 지역 밖이면 DB를 조회하지 않고 빈 목록을 반환한다")
    void 서비스_지역_밖은_빈_목록(double latitude, double longitude) {
        assertThat(service.findNearbyStores(request(latitude, longitude, null, null))).isEmpty();
        assertThat(service.findNearbyStores(
                request(latitude, longitude, null, null, Set.of(StoreServiceType.Code.USIM_REISSUE)))).isEmpty();
        verify(repository, never()).findNearest(any());
        verify(repository, never()).findNearestMatching(any());
        verify(repository, never()).findMatchingWithinRadius(any());
        verify(repository, never()).findNearestCandidatesMatching(any(), anyInt());
    }

    @ParameterizedTest(name = "위도 {0}, 경도 {1}")
    @CsvSource({"33.0, 124.5", "38.7, 132.0", "37.2426, 131.8597"})
    @DisplayName("국내 서비스 지역의 경계와 끝자락(독도)은 검색한다")
    void 서비스_지역_경계는_검색한다(double latitude, double longitude) {
        service.findNearbyStores(request(latitude, longitude, null, null));

        verify(repository).findNearest(any());
    }

    @ParameterizedTest(name = "반경 {0}m")
    @CsvSource({"0", "-1", "10001"})
    @DisplayName("반경이 허용 범위(최대 10km)를 벗어나면 거부한다")
    void 잘못된_반경은_거부한다(int radiusMeters) {
        assertErrorCode(request(LATITUDE, LONGITUDE, radiusMeters, null), StoreErrorCode.INVALID_SEARCH_RADIUS);
    }

    @Test
    @DisplayName("반경 상한값 10km는 허용한다")
    void 반경_상한값은_허용한다() {
        service.findNearbyStores(request(LATITUDE, LONGITUDE, 10000, null));

        assertThat(capturedQuery().radiusMeters()).isEqualTo(10000);
    }

    @ParameterizedTest(name = "개수 {0}")
    @CsvSource({"0", "-1", "21"})
    @DisplayName("개수가 허용 범위를 벗어나면 거부한다")
    void 잘못된_개수는_거부한다(int limit) {
        assertErrorCode(request(LATITUDE, LONGITUDE, null, limit), StoreErrorCode.INVALID_SEARCH_LIMIT);
    }

    @Test
    @DisplayName("기본값이 최대값을 넘는 설정은 기동 시점에 막는다")
    void 잘못된_설정은_막는다() {
        assertThatThrownBy(() -> new StoreSearchProperties(20000, 10000, 5, 20, Duration.ofSeconds(2), false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StoreSearchProperties(10000, 10000, 30, 20, Duration.ofSeconds(2), false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StoreSearchProperties(10000, 0, 5, 20, Duration.ofSeconds(2), false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StoreSearchProperties(10000, 10000, 5, 20, Duration.ofMillis(500), false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private StoreSearchProperties properties(boolean openNowFilterEnabled) {
        return new StoreSearchProperties(10000, 10000, 5, 20, Duration.ofSeconds(2), openNowFilterEnabled);
    }

    private StoreSearchService enabledService(Clock clock) {
        return new StoreSearchService(repository, new StoreConverter(), properties(true), clock);
    }

    // 필터가 없으면 저장소의 KNN으로, 있으면 필터 전략으로 간다
    private StoreNearbyQueryRepository.Query capturedQuery() {
        ArgumentCaptor<StoreNearbyQueryRepository.Query> captor =
                ArgumentCaptor.forClass(StoreNearbyQueryRepository.Query.class);
        verify(repository).findNearest(captor.capture());
        return captor.getValue();
    }

    private StoreNearbyQueryRepository.Query capturedFilteredQuery() {
        ArgumentCaptor<StoreNearbyQueryRepository.Query> captor =
                ArgumentCaptor.forClass(StoreNearbyQueryRepository.Query.class);
        verify(repository).findNearestMatching(captor.capture());
        verify(repository, never()).findNearest(any());
        verify(repository, never()).findMatchingWithinRadius(any());
        verify(repository, never()).findNearestCandidatesMatching(any(), anyInt());
        return captor.getValue();
    }

    // 영업 중 조건이 섞이면 가까운 후보 확인부터 한다
    private StoreNearbyQueryRepository.Query capturedDynamicQuery() {
        ArgumentCaptor<StoreNearbyQueryRepository.Query> captor =
                ArgumentCaptor.forClass(StoreNearbyQueryRepository.Query.class);
        verify(repository).findNearestCandidatesMatching(captor.capture(), anyInt());
        verify(repository, never()).findNearest(any());
        verify(repository, never()).findNearestMatching(any());
        return captor.getValue();
    }

    private void assertSearchTimeout(StoreNearbySearchRequest request) {
        assertThatThrownBy(() -> service.findNearbyStores(request))
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getErrorCode())
                .isEqualTo(StoreErrorCode.SEARCH_TIMEOUT);
    }

    private void assertErrorCode(StoreNearbySearchRequest request, StoreErrorCode errorCode) {
        assertThatThrownBy(() -> service.findNearbyStores(request))
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getErrorCode())
                .isEqualTo(errorCode);
        verify(repository, never()).findMatchingWithinRadius(any());
        verify(repository, never()).findNearestMatching(any());
        verify(repository, never()).findNearest(any());
        verify(repository, never()).findNearestCandidatesMatching(any(), anyInt());
    }

    private StoreNearbySearchRequest request(Double latitude, Double longitude, Integer radiusMeters, Integer limit) {
        return request(latitude, longitude, radiusMeters, limit, null);
    }

    private StoreNearbySearchRequest request(
            Double latitude, Double longitude, Integer radiusMeters, Integer limit,
            Set<StoreServiceType.Code> serviceTypes
    ) {
        return StoreNearbySearchRequest.builder()
                .latitude(latitude)
                .longitude(longitude)
                .radiusMeters(radiusMeters)
                .limit(limit)
                .serviceTypes(serviceTypes)
                .build();
    }

    private StoreNearbySearchRequest openNowRequest(Set<StoreServiceType.Code> serviceTypes, Integer limit) {
        return StoreNearbySearchRequest.builder()
                .latitude(LATITUDE)
                .longitude(LONGITUDE)
                .limit(limit)
                .serviceTypes(serviceTypes)
                .openNow(true)
                .build();
    }

    private StoreNearbyQueryRepository.Row row(long storeId, double distanceMeters) {
        return new StoreNearbyQueryRepository.Row(storeId, "매장" + storeId, null, "주소",
                BigDecimal.valueOf(LATITUDE), BigDecimal.valueOf(LONGITUDE), distanceMeters);
    }
}
