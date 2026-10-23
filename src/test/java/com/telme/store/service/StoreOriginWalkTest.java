package com.telme.store.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telme.global.common.exception.GeneralException;
import com.telme.store.config.StoreOriginWalkProperties;
import com.telme.store.config.StoreRouteProperties;
import com.telme.store.dto.req.RouteMode;
import com.telme.store.dto.req.StoreOriginWalkRequest;
import com.telme.store.dto.res.StoreRouteResponse;
import com.telme.store.entity.Store;
import com.telme.store.exception.StoreErrorCode;
import jakarta.validation.Validation;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class StoreOriginWalkTest {
    private final StoreQueryService stores = mock(StoreQueryService.class);
    private final StoreRouteUsageGuard guard = mock(StoreRouteUsageGuard.class);
    private final List<StoreRouteClient> clients = Arrays.stream(RouteMode.values()).map(mode -> {
        StoreRouteClient client = mock(StoreRouteClient.class);
        when(client.mode()).thenReturn(mode);
        return client;
    }).toList();
    private final StoreRouteClient walk = clients.stream()
            .filter(c -> c.mode() == RouteMode.WALK).findFirst().orElseThrow();
    private StoreRouteService service;
    private final StoreOriginWalkRequest request = new StoreOriginWalkRequest(37.5444, 127.0374, 37.5447, 127.0366);

    @BeforeEach
    void setUp() {
        when(stores.getOpenStore(101L)).thenReturn(Store.builder().storeId(101L).build());
        service = new StoreRouteService(stores, clients, new StoreRouteProperties(1000), guard,
                new StoreOriginWalkGuard(new StoreOriginWalkProperties(3000, 30, List.of()), Clock.systemUTC()));
    }

    @Test
    void 선택한_첫_좌표로_도보만_조회하고_좌표를_반올림하지_않는다() {
        StoreRouteResponse response = new StoreRouteResponse(RouteMode.WALK, 120, 100, null, null, List.of());
        when(walk.findRoute(any())).thenReturn(response);
        assertThat(service.getOriginWalk(101L, request, "127.0.0.1")).isSameAs(response);
        ArgumentCaptor<RouteEndpoints> endpoints = ArgumentCaptor.forClass(RouteEndpoints.class);
        verify(walk).findRoute(endpoints.capture());
        assertThat(endpoints.getValue().originLatitude()).isEqualByComparingTo("37.5444");
        assertThat(endpoints.getValue().destinationLatitude()).isEqualByComparingTo("37.5447");
        assertThat(endpoints.getValue().destinationLongitude()).isEqualByComparingTo("127.0366");
        clients.stream().filter(c -> c != walk).forEach(c -> verify(c, never()).findRoute(any()));
    }

    @Test
    void 없는_매장이면_외부_도보를_조회하지_않는다() {
        when(stores.getOpenStore(101L)).thenThrow(new GeneralException(StoreErrorCode.STORE_NOT_FOUND));
        assertThatThrownBy(() -> service.getOriginWalk(101L, request, "127.0.0.1")).isInstanceOf(GeneralException.class);
        verify(walk, never()).findRoute(any());
    }

    @Test
    void 운영_중단이면_매장과_외부_도보를_조회하지_않는다() {
        doThrow(new GeneralException(StoreErrorCode.ROUTE_DISABLED)).when(guard).checkEnabled();
        assertThatThrownBy(() -> service.getOriginWalk(101L, request, "127.0.0.1")).isInstanceOf(GeneralException.class);
        verify(stores, never()).getOpenStore(anyLong());
        verify(walk, never()).findRoute(any());
    }

    @Test
    void 기존_최대_거리와_도보_예산_오류를_그대로_적용한다() {
        when(walk.findRoute(any())).thenReturn(
                new StoreRouteResponse(RouteMode.WALK, 1001, 100, null, null, List.of()));
        assertThatThrownBy(() -> service.getOriginWalk(101L, request, "127.0.0.1"))
                .isInstanceOfSatisfying(GeneralException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(StoreErrorCode.ROUTE_DISTANCE_EXCEEDED));
        when(walk.findRoute(any())).thenThrow(new GeneralException(StoreErrorCode.ROUTE_DAILY_LIMIT_EXCEEDED));
        assertThatThrownBy(() -> service.getOriginWalk(101L, request, "127.0.0.1"))
                .isInstanceOfSatisfying(GeneralException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(StoreErrorCode.ROUTE_DAILY_LIMIT_EXCEEDED));
    }


    @Test
    void 장거리_요청은_실제거리_제한이_없어도_외부_호출_전에_거절한다() {
        service = new StoreRouteService(stores, clients, new StoreRouteProperties(0), guard,
                new StoreOriginWalkGuard(new StoreOriginWalkProperties(3000, 30, List.of()), Clock.systemUTC()));
        var far = new StoreOriginWalkRequest(37.5665, 126.9780, 35.1796, 129.0756);
        assertThatThrownBy(() -> service.getOriginWalk(101L, far, "192.0.2.1"))
                .isInstanceOfSatisfying(GeneralException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(StoreErrorCode.ROUTE_DISTANCE_EXCEEDED));
        verify(walk, never()).findRoute(any());
        verify(stores, never()).getOpenStore(anyLong());
    }

    @Test
    void 경로별_요청을_허용하고_빈도_초과는_외부_호출_전에_거절한다() {
        when(walk.findRoute(any())).thenReturn(StoreRouteResponse.arrived(RouteMode.WALK));
        for (int i = 0; i < 30; i++) {
            var selected = new StoreOriginWalkRequest(37.5444, 127.0374, 37.5447 + i * 0.00001, 127.0366);
            service.getOriginWalk(101L, selected, "192.0.2.1");
        }
        assertThatThrownBy(() -> service.getOriginWalk(102L, request, "192.0.2.1"))
                .isInstanceOfSatisfying(GeneralException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(StoreErrorCode.ORIGIN_WALK_RATE_LIMIT_EXCEEDED));
        verify(walk, org.mockito.Mockito.times(30)).findRoute(any());
        verify(stores, never()).getOpenStore(102L);
        service.getOriginWalk(101L, request, "192.0.2.2");
        verify(walk, org.mockito.Mockito.times(31)).findRoute(any());
    }

    @Test
    void 거리로_거절된_요청은_분당_횟수를_차감하지_않는다() {
        service = new StoreRouteService(stores, clients, new StoreRouteProperties(0), guard,
                new StoreOriginWalkGuard(new StoreOriginWalkProperties(3000, 1, List.of()), Clock.systemUTC()));
        assertThatThrownBy(() -> service.getOriginWalk(101L,
                new StoreOriginWalkRequest(37.5665, 126.9780, 35.1796, 129.0756), "192.0.2.1"))
                .isInstanceOf(GeneralException.class);
        when(walk.findRoute(any())).thenReturn(StoreRouteResponse.arrived(RouteMode.WALK));
        assertThat(service.getOriginWalk(101L, request, "192.0.2.1")).isNotNull();
        verify(walk).findRoute(any());
    }

    @Test
    void 누락과_해외_좌표는_검증에_실패한다() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(validator.validate(request)).isEmpty();
            assertThat(validator.validate(new StoreOriginWalkRequest(null, 127.0, 37.5, 127.0))).isNotEmpty();
            assertThat(validator.validate(new StoreOriginWalkRequest(37.5, 127.0, 37.5, 139.0))).isNotEmpty();
        }
    }
}
