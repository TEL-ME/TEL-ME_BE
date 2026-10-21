package com.telme.store.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telme.global.common.exception.GeneralException;
import com.telme.store.config.StoreRouteCacheProperties;
import com.telme.store.config.StoreRouteProperties;
import com.telme.store.dto.req.RouteMode;
import com.telme.store.dto.req.StoreRouteRequest;
import com.telme.store.dto.res.StoreRouteResponse;
import com.telme.store.entity.Store;
import com.telme.store.exception.StoreErrorCode;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

class StoreRouteServiceTest {

    private static final long STORE_ID = 7L;

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-21T00:00:00Z"));
    private final StoreQueryService storeQueryService = mock(StoreQueryService.class);
    private final StoreRouteClient carClient = mockClient(RouteMode.CAR);
    private final StoreRouteClient transitClient = mockClient(RouteMode.TRANSIT);
    private final StoreRouteClient walkClient = mockClient(RouteMode.WALK);
    private final StoreRouteClient bicycleClient = mockClient(RouteMode.BICYCLE);
    private StoreRouteService service;

    @BeforeEach
    void setUp() {
        Store store = Store.builder()
                .storeId(STORE_ID)
                .latitude(new BigDecimal("37.497942"))
                .longitude(new BigDecimal("127.027621"))
                .build();
        when(storeQueryService.getOpenStore(STORE_ID)).thenReturn(store);
        service = new StoreRouteService(storeQueryService, new StoreRouteCache(clock, defaultCacheProperties()),
                List.of(carClient, transitClient, walkClient, bicycleClient), new StoreRouteProperties(0));
    }

    @Test
    @DisplayName("이동 수단에 맞는 클라이언트로 매장 좌표까지의 경로를 조회한다")
    void 수단별_클라이언트_호출() {
        StoreRouteResponse expected = StoreRouteResponse.arrived(RouteMode.TRANSIT);
        when(transitClient.findRoute(any())).thenReturn(expected);

        StoreRouteResponse response = service.getRoute(STORE_ID, request(RouteMode.TRANSIT, 37.5663, 126.9779));

        assertThat(response).isSameAs(expected);
        ArgumentCaptor<RouteEndpoints> captor = ArgumentCaptor.forClass(RouteEndpoints.class);
        verify(transitClient).findRoute(captor.capture());
        assertThat(captor.getValue().destinationLatitude()).isEqualByComparingTo("37.497942");
        assertThat(captor.getValue().destinationLongitude()).isEqualByComparingTo("127.027621");
        verify(carClient, never()).findRoute(any());
    }

    @Test
    @DisplayName("출발지는 자동차·대중교통은 소수점 3자리, 도보·자전거는 4자리로 반올림해 조회한다")
    void 출발지_반올림() {
        when(carClient.findRoute(any())).thenReturn(StoreRouteResponse.arrived(RouteMode.CAR));
        when(walkClient.findRoute(any())).thenReturn(StoreRouteResponse.arrived(RouteMode.WALK));

        service.getRoute(STORE_ID, request(RouteMode.CAR, 37.56634, 126.97796));
        service.getRoute(STORE_ID, request(RouteMode.WALK, 37.56634, 126.97796));

        ArgumentCaptor<RouteEndpoints> car = ArgumentCaptor.forClass(RouteEndpoints.class);
        verify(carClient).findRoute(car.capture());
        assertThat(car.getValue().originLatitude()).isEqualByComparingTo("37.566");
        assertThat(car.getValue().originLongitude()).isEqualByComparingTo("126.978");
        ArgumentCaptor<RouteEndpoints> walk = ArgumentCaptor.forClass(RouteEndpoints.class);
        verify(walkClient).findRoute(walk.capture());
        assertThat(walk.getValue().originLatitude()).isEqualByComparingTo("37.5663");
        assertThat(walk.getValue().originLongitude()).isEqualByComparingTo("126.9780");
    }

    @Test
    @DisplayName("반올림하면 같은 출발지는 유효 기간 안에서 다시 조회하지 않는다")
    void 같은_출발지는_재사용() {
        when(carClient.findRoute(any())).thenReturn(StoreRouteResponse.arrived(RouteMode.CAR));

        service.getRoute(STORE_ID, request(RouteMode.CAR, 37.56634, 126.97796));
        clock.advance(Duration.ofMinutes(1));
        service.getRoute(STORE_ID, request(RouteMode.CAR, 37.56601, 126.97801));

        verify(carClient, times(1)).findRoute(any());
    }

    @Test
    @DisplayName("자동차 경로는 2분이 지나면 다시 조회하고, 다른 수단의 경로는 따로 저장한다")
    void 수단별_유효_기간() {
        when(carClient.findRoute(any())).thenReturn(StoreRouteResponse.arrived(RouteMode.CAR));
        when(walkClient.findRoute(any())).thenReturn(StoreRouteResponse.arrived(RouteMode.WALK));

        service.getRoute(STORE_ID, request(RouteMode.CAR, 37.5663, 126.9779));
        service.getRoute(STORE_ID, request(RouteMode.WALK, 37.5663, 126.9779));
        clock.advance(Duration.ofMinutes(3));
        service.getRoute(STORE_ID, request(RouteMode.CAR, 37.5663, 126.9779));
        service.getRoute(STORE_ID, request(RouteMode.WALK, 37.5663, 126.9779));

        verify(carClient, times(2)).findRoute(any());
        verify(walkClient, times(1)).findRoute(any());
    }

    @Test
    @DisplayName("조회에 실패하면 저장하지 않아 다음 요청이 다시 조회한다")
    void 실패는_저장하지_않음() {
        when(carClient.findRoute(any()))
                .thenThrow(new GeneralException(StoreErrorCode.ROUTE_UNAVAILABLE))
                .thenReturn(StoreRouteResponse.arrived(RouteMode.CAR));

        assertThatThrownBy(() -> service.getRoute(STORE_ID, request(RouteMode.CAR, 37.5663, 126.9779)))
                .isInstanceOf(GeneralException.class);
        StoreRouteResponse retried = service.getRoute(STORE_ID, request(RouteMode.CAR, 37.5663, 126.9779));

        assertThat(retried).isEqualTo(StoreRouteResponse.arrived(RouteMode.CAR));
        verify(carClient, times(2)).findRoute(any());
    }

    @Test
    @DisplayName("없거나 폐점한 매장이면 경로를 조회하지 않고 404다")
    void 매장_없음() {
        when(storeQueryService.getOpenStore(99L)).thenThrow(new GeneralException(StoreErrorCode.STORE_NOT_FOUND));

        assertThatThrownBy(() -> service.getRoute(99L, request(RouteMode.CAR, 37.5663, 126.9779)))
                .isInstanceOfSatisfying(GeneralException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(StoreErrorCode.STORE_NOT_FOUND));
        verify(carClient, never()).findRoute(any());
    }

    @Test
    @DisplayName("이동 수단 중 클라이언트가 없는 것이 있으면 시작할 때 실패한다")
    void 클라이언트_누락() {
        assertThatThrownBy(() -> new StoreRouteService(storeQueryService,
                new StoreRouteCache(clock, defaultCacheProperties()),
                List.of(carClient, transitClient, walkClient), new StoreRouteProperties(0)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BICYCLE");
    }

    @Test
    @DisplayName("조정한 정밀도의 같은 출발지는 같은 외부 요청 좌표와 캐시를 사용한다")
    void 설정한_정밀도와_캐시_일치() {
        StoreRouteCacheProperties properties = new StoreRouteCacheProperties(Duration.ofMinutes(2),
                Duration.ofMinutes(5), Duration.ofMinutes(10), Duration.ofMinutes(10), 5, 6);
        service = new StoreRouteService(storeQueryService, new StoreRouteCache(clock, properties),
                List.of(carClient, transitClient, walkClient, bicycleClient), new StoreRouteProperties(0));
        when(carClient.findRoute(any())).thenReturn(StoreRouteResponse.arrived(RouteMode.CAR));

        service.getRoute(STORE_ID, request(RouteMode.CAR, 37.566349, 126.977969));
        service.getRoute(STORE_ID, request(RouteMode.CAR, 37.566351, 126.977971));

        ArgumentCaptor<RouteEndpoints> captor = ArgumentCaptor.forClass(RouteEndpoints.class);
        verify(carClient).findRoute(captor.capture());
        assertThat(captor.getValue().originLatitude()).isEqualByComparingTo("37.56635");
        assertThat(captor.getValue().originLongitude()).isEqualByComparingTo("126.97797");
    }

    @ParameterizedTest
    @EnumSource(RouteMode.class)
    @DisplayName("거리 제한이 0이면 모든 수단의 장거리 경로를 그대로 반환한다")
    void 거리_제한_없음(RouteMode mode) {
        StoreRouteClient client = findClient(mode);
        StoreRouteResponse expected = routeResponse(mode, 1_000_000);
        when(client.findRoute(any())).thenReturn(expected);

        assertThat(service.getRoute(STORE_ID, request(mode, 37.5663, 126.9779))).isSameAs(expected);
    }

    @ParameterizedTest
    @EnumSource(RouteMode.class)
    @DisplayName("설정 거리와 같으면 허용하고 같은 요청은 캐시를 재사용한다")
    void 거리_상한_포함(RouteMode mode) {
        service = limitedService(20_000);
        StoreRouteClient client = findClient(mode);
        StoreRouteResponse expected = routeResponse(mode, 20_000);
        when(client.findRoute(any())).thenReturn(expected);

        assertThat(service.getRoute(STORE_ID, request(mode, 37.5663, 126.9779))).isSameAs(expected);
        assertThat(service.getRoute(STORE_ID, request(mode, 37.5663, 126.9779))).isSameAs(expected);

        verify(client).findRoute(any());
    }

    @ParameterizedTest
    @EnumSource(RouteMode.class)
    @DisplayName("상한을 1m 초과하면 거리 초과 오류이고 실패한 경로는 캐시하지 않는다")
    void 거리_상한_초과(RouteMode mode) {
        service = limitedService(20_000);
        StoreRouteClient client = findClient(mode);
        when(client.findRoute(any())).thenReturn(routeResponse(mode, 20_001));

        for (int attempt = 0; attempt < 2; attempt++) {
            assertThatThrownBy(() -> service.getRoute(STORE_ID, request(mode, 37.5663, 126.9779)))
                    .isInstanceOfSatisfying(GeneralException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(StoreErrorCode.ROUTE_DISTANCE_EXCEEDED));
        }
        verify(client, times(2)).findRoute(any());
    }

    private StoreRouteService limitedService(int maxDistance) {
        return new StoreRouteService(storeQueryService, new StoreRouteCache(clock, defaultCacheProperties()),
                List.of(carClient, transitClient, walkClient, bicycleClient), new StoreRouteProperties(maxDistance));
    }

    private StoreRouteClient findClient(RouteMode mode) {
        return switch (mode) {
            case CAR -> carClient;
            case TRANSIT -> transitClient;
            case WALK -> walkClient;
            case BICYCLE -> bicycleClient;
        };
    }

    private static StoreRouteResponse routeResponse(RouteMode mode, int distance) {
        return StoreRouteResponse.builder().mode(mode).distanceMeters(distance).durationSeconds(600)
                .steps(List.of()).build();
    }

    private static StoreRouteCacheProperties defaultCacheProperties() {
        return new StoreRouteCacheProperties(Duration.ofMinutes(2), Duration.ofMinutes(5),
                Duration.ofMinutes(10), Duration.ofMinutes(10), 3, 4);
    }

    private static StoreRouteRequest request(RouteMode mode, double latitude, double longitude) {
        return new StoreRouteRequest(mode, latitude, longitude);
    }

    private static StoreRouteClient mockClient(RouteMode mode) {
        StoreRouteClient client = mock(StoreRouteClient.class);
        when(client.mode()).thenReturn(mode);
        return client;
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
