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
import com.telme.store.config.StoreRouteProperties;
import com.telme.store.dto.req.RouteMode;
import com.telme.store.dto.req.StoreOriginWalkRequest;
import com.telme.store.dto.res.StoreRouteResponse;
import com.telme.store.entity.Store;
import com.telme.store.exception.StoreErrorCode;
import jakarta.validation.Validation;
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
        service = new StoreRouteService(stores, clients, new StoreRouteProperties(1000), guard);
    }

    @Test
    void 선택한_첫_좌표로_도보만_조회하고_좌표를_반올림하지_않는다() {
        StoreRouteResponse response = new StoreRouteResponse(RouteMode.WALK, 120, 100, null, null, List.of());
        when(walk.findRoute(any())).thenReturn(response);
        assertThat(service.getOriginWalk(101L, request)).isSameAs(response);
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
        assertThatThrownBy(() -> service.getOriginWalk(101L, request)).isInstanceOf(GeneralException.class);
        verify(walk, never()).findRoute(any());
    }

    @Test
    void 운영_중단이면_매장과_외부_도보를_조회하지_않는다() {
        doThrow(new GeneralException(StoreErrorCode.ROUTE_DISABLED)).when(guard).checkEnabled();
        assertThatThrownBy(() -> service.getOriginWalk(101L, request)).isInstanceOf(GeneralException.class);
        verify(stores, never()).getOpenStore(anyLong());
        verify(walk, never()).findRoute(any());
    }

    @Test
    void 기존_최대_거리와_도보_예산_오류를_그대로_적용한다() {
        when(walk.findRoute(any())).thenReturn(
                new StoreRouteResponse(RouteMode.WALK, 1001, 100, null, null, List.of()));
        assertThatThrownBy(() -> service.getOriginWalk(101L, request))
                .isInstanceOfSatisfying(GeneralException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(StoreErrorCode.ROUTE_DISTANCE_EXCEEDED));
        when(walk.findRoute(any())).thenThrow(new GeneralException(StoreErrorCode.ROUTE_DAILY_LIMIT_EXCEEDED));
        assertThatThrownBy(() -> service.getOriginWalk(101L, request))
                .isInstanceOfSatisfying(GeneralException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(StoreErrorCode.ROUTE_DAILY_LIMIT_EXCEEDED));
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
