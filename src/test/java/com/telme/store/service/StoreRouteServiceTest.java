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
import com.telme.store.config.StoreRouteProperties;
import com.telme.store.dto.req.RouteMode;
import com.telme.store.dto.req.StoreRouteRequest;
import com.telme.store.dto.res.StoreRouteResponse;
import com.telme.store.entity.Store;
import com.telme.store.exception.StoreErrorCode;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

class StoreRouteServiceTest {

    private static final long STORE_ID = 7L;

    private final StoreQueryService storeQueryService = mock(StoreQueryService.class);
    private final StoreRouteClient carClient = mockClient(RouteMode.CAR);
    private final StoreRouteClient transitClient = mockClient(RouteMode.TRANSIT);
    private final StoreRouteClient walkClient = mockClient(RouteMode.WALK);
    private final StoreRouteClient bicycleClient = mockClient(RouteMode.BICYCLE);
    private final StoreRouteUsageGuard usageGuard = mock(StoreRouteUsageGuard.class);
    private StoreRouteService service;

    @BeforeEach
    void setUp() {
        Store store = Store.builder()
                .storeId(STORE_ID)
                .latitude(new BigDecimal("37.497942"))
                .longitude(new BigDecimal("127.027621"))
                .build();
        when(storeQueryService.getOpenStore(STORE_ID)).thenReturn(store);
        service = new StoreRouteService(storeQueryService,
                List.of(carClient, transitClient, walkClient, bicycleClient), new StoreRouteProperties(0), usageGuard);
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

    @ParameterizedTest
    @EnumSource(RouteMode.class)
    @DisplayName("모든 수단은 같은 요청도 매번 조회하고 출발 좌표를 그대로 전달한다")
    void 같은_요청도_원본_좌표로_매번_조회(RouteMode mode) {
        StoreRouteClient client = findClient(mode);
        when(client.findRoute(any())).thenReturn(StoreRouteResponse.arrived(mode));

        service.getRoute(STORE_ID, request(mode, 37.566349, 126.977969));
        service.getRoute(STORE_ID, request(mode, 37.566349, 126.977969));

        ArgumentCaptor<RouteEndpoints> captor = ArgumentCaptor.forClass(RouteEndpoints.class);
        verify(client, times(2)).findRoute(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(endpoints -> {
            assertThat(endpoints.originLatitude()).isEqualByComparingTo("37.566349");
            assertThat(endpoints.originLongitude()).isEqualByComparingTo("126.977969");
            assertThat(endpoints.destinationLatitude()).isEqualByComparingTo("37.497942");
            assertThat(endpoints.destinationLongitude()).isEqualByComparingTo("127.027621");
        });
    }

    @Test
    @DisplayName("조회에 실패해도 다음 요청은 다시 조회한다")
    void 실패_후_재조회() {
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
    @DisplayName("기능 OFF면 매장 DB 조회와 외부 호출 전에 차단한다")
    void 기능_중단() {
        org.mockito.Mockito.doThrow(new GeneralException(StoreErrorCode.ROUTE_DISABLED))
                .when(usageGuard).checkEnabled();
        assertThatThrownBy(() -> service.getRoute(STORE_ID, request(RouteMode.CAR, 37.5, 127)))
                .isInstanceOfSatisfying(GeneralException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(StoreErrorCode.ROUTE_DISABLED));
        verify(storeQueryService, never()).getOpenStore(org.mockito.ArgumentMatchers.anyLong());
        verify(carClient, never()).findRoute(any());
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
                List.of(carClient, transitClient, walkClient), new StoreRouteProperties(0), usageGuard))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BICYCLE");
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
    @DisplayName("설정 거리와 같으면 허용하고 같은 요청도 매번 조회한다")
    void 거리_상한_포함(RouteMode mode) {
        service = limitedService(20_000);
        StoreRouteClient client = findClient(mode);
        StoreRouteResponse expected = routeResponse(mode, 20_000);
        when(client.findRoute(any())).thenReturn(expected);

        assertThat(service.getRoute(STORE_ID, request(mode, 37.5663, 126.9779))).isSameAs(expected);
        assertThat(service.getRoute(STORE_ID, request(mode, 37.5663, 126.9779))).isSameAs(expected);

        verify(client, times(2)).findRoute(any());
    }

    @ParameterizedTest
    @EnumSource(RouteMode.class)
    @DisplayName("상한을 1m 초과하면 매 요청마다 거리 초과 오류다")
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
        return new StoreRouteService(storeQueryService,
                List.of(carClient, transitClient, walkClient, bicycleClient),
                new StoreRouteProperties(maxDistance), usageGuard);
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

    private static StoreRouteRequest request(RouteMode mode, double latitude, double longitude) {
        return new StoreRouteRequest(mode, latitude, longitude);
    }

    private static StoreRouteClient mockClient(RouteMode mode) {
        StoreRouteClient client = mock(StoreRouteClient.class);
        when(client.mode()).thenReturn(mode);
        return client;
    }

}
