package com.telme.store.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telme.store.config.StoreRouteCacheProperties;
import com.telme.store.dto.req.RouteMode;
import com.telme.store.dto.res.StoreRouteResponse;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class StoreRouteCacheTest {

    private static final RouteEndpoints ENDPOINTS = new RouteEndpoints(
            new BigDecimal("37.566349"), new BigDecimal("126.977969"),
            new BigDecimal("37.497942"), new BigDecimal("127.027621"));
    private static final StoreRouteCacheProperties PROPERTIES = new StoreRouteCacheProperties(
            Duration.ofSeconds(7), Duration.ofSeconds(11), Duration.ofSeconds(13), Duration.ofSeconds(17), 5, 6);

    @ParameterizedTest
    @EnumSource(RouteMode.class)
    @DisplayName("수단별로 변경한 TTL까지 재사용하고 정확히 만료되면 다시 조회한다")
    @SuppressWarnings("unchecked")
    void 설정한_TTL_적용(RouteMode mode) {
        Clock clock = mock(Clock.class);
        Instant now = Instant.parse("2026-10-22T00:00:00Z");
        when(clock.instant()).thenReturn(now);
        StoreRouteCache cache = new StoreRouteCache(clock, PROPERTIES);
        Supplier<StoreRouteResponse> loader = mock(Supplier.class);
        StoreRouteResponse expected = StoreRouteResponse.arrived(mode);
        when(loader.get()).thenReturn(expected);
        Duration ttl = switch (mode) {
            case CAR -> PROPERTIES.carTtl();
            case TRANSIT -> PROPERTIES.transitTtl();
            case WALK -> PROPERTIES.walkTtl();
            case BICYCLE -> PROPERTIES.bicycleTtl();
        };

        assertThat(cache.getOrLoad(mode, ENDPOINTS, loader)).isSameAs(expected);
        when(clock.instant()).thenReturn(now.plus(ttl).minusNanos(1));
        assertThat(cache.getOrLoad(mode, ENDPOINTS, loader)).isSameAs(expected);
        verify(loader).get();
        when(clock.instant()).thenReturn(now.plus(ttl));
        cache.getOrLoad(mode, ENDPOINTS, loader);
        verify(loader, times(2)).get();
    }

    @ParameterizedTest
    @EnumSource(RouteMode.class)
    @DisplayName("설정한 그룹별 정밀도로 출발지만 반올림하고 매장 좌표는 유지한다")
    void 설정한_정밀도_적용(RouteMode mode) {
        StoreRouteCache cache = new StoreRouteCache(Clock.systemUTC(), PROPERTIES);

        RouteEndpoints rounded = cache.roundOrigin(mode, ENDPOINTS);

        boolean carOrTransit = mode == RouteMode.CAR || mode == RouteMode.TRANSIT;
        assertThat(rounded.originLatitude()).isEqualByComparingTo(carOrTransit ? "37.56635" : "37.566349");
        assertThat(rounded.originLongitude()).isEqualByComparingTo(carOrTransit ? "126.97797" : "126.977969");
        assertThat(rounded.destinationLatitude()).isEqualTo(ENDPOINTS.destinationLatitude());
        assertThat(rounded.destinationLongitude()).isEqualTo(ENDPOINTS.destinationLongitude());
    }
}
