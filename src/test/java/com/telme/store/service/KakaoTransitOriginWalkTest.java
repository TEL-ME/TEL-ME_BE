package com.telme.store.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.telme.global.common.exception.GeneralException;
import com.telme.store.config.KakaoRouteProperties;
import com.telme.store.converter.StoreRouteConverter;
import com.telme.store.dto.req.RouteMode;
import com.telme.store.dto.res.StoreRouteResponse;
import com.telme.store.dto.res.StoreRouteResponse.Coordinate;
import com.telme.store.dto.res.StoreRouteResponse.Step;
import com.telme.store.dto.res.StoreRouteResponse.StepType;
import com.telme.store.exception.StoreErrorCode;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KakaoTransitOriginWalkTest {

    private final KakaoRouteCalls calls = new KakaoRouteCalls(mock(StoreRouteUsageGuard.class));

    private static final RouteEndpoints ENDPOINTS = new RouteEndpoints(
            new BigDecimal("37.566"), new BigDecimal("126.978"),
            new BigDecimal("37.497942"), new BigDecimal("127.027621"));
    private static final Step WALK_STEP = new Step(StepType.WALK, "정류장까지 도보", 120, 100, null,
            List.of(new Coordinate(37.566, 126.978), new Coordinate(37.567, 126.978)));
    private static final StoreRouteResponse WALK = StoreRouteResponse.builder()
            .mode(RouteMode.WALK).distanceMeters(120).durationSeconds(100).steps(List.of(WALK_STEP)).build();

    private final KakaoWalkRouteClient walkClient = mock(KakaoWalkRouteClient.class);
    private MockRestServiceServer server;
    private RestClient restClient;
    private KakaoTransitRouteClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://kakao.test");
        server = MockRestServiceServer.bindTo(builder).build();
        restClient = builder.build();
        client = createClient(true);
        when(walkClient.findRoute(any())).thenReturn(WALK);
    }

    @Test
    @DisplayName("스위치 기본값은 꺼짐이고 명시적으로 켤 수 있다")
    void 설정_바인딩() {
        for (boolean enabled : new boolean[] {false, true}) {
            MapConfigurationPropertySource source = new MapConfigurationPropertySource();
            source.put("kakao.route.rest-api-key", "test-key");
            if (enabled) {
                source.put("kakao.route.transit-origin-walk-enabled", "true");
            }
            KakaoRouteProperties properties = new Binder(source)
                    .bind("kakao.route", KakaoRouteProperties.class).get();
            assertThat(properties.transitOriginWalkEnabled()).isEqualTo(enabled);
        }
    }

    @Test
    @DisplayName("스위치가 꺼져 있으면 첫 좌표가 멀어도 도보를 호출하지 않는다")
    void 스위치_꺼짐() {
        client = createClient(false);
        expectTransit("[[126.978,37.567]]");

        StoreRouteResponse result = client.findRoute(ENDPOINTS);

        assertThat(result.distanceMeters()).isEqualTo(1000);
        assertThat(result.steps()).hasSize(1);
        verify(walkClient, never()).findRoute(any());
    }

    @Test
    @DisplayName("도보 단계를 앞에 붙이고 거리·시간만 합산하며 요금·환승·원래 단계는 유지한다")
    void 도보_추가() {
        expectTransit("[[126.978,37.567]]");

        StoreRouteResponse result = client.findRoute(ENDPOINTS);

        assertThat(result.mode()).isEqualTo(RouteMode.TRANSIT);
        assertThat(result.distanceMeters()).isEqualTo(1120);
        assertThat(result.durationSeconds()).isEqualTo(700);
        assertThat(result.fare()).isEqualTo(1500);
        assertThat(result.transfers()).isEqualTo(1);
        assertThat(result.steps()).hasSize(2);
        assertThat(result.steps().get(0)).isEqualTo(WALK_STEP);
        assertThat(result.steps().get(1).type()).isEqualTo(StepType.BUS);
        assertThat(result.steps().get(1).lineName()).isEqualTo("76");
        ArgumentCaptor<RouteEndpoints> captor = ArgumentCaptor.forClass(RouteEndpoints.class);
        verify(walkClient).findRoute(captor.capture());
        assertThat(captor.getValue().originLatitude()).isEqualByComparingTo("37.566");
        assertThat(captor.getValue().originLongitude()).isEqualByComparingTo("126.978");
        assertThat(captor.getValue().destinationLatitude()).isEqualByComparingTo("37.567");
        assertThat(captor.getValue().destinationLongitude()).isEqualByComparingTo("126.978");
        server.verify();
    }

    @Test
    @DisplayName("출발지에서 30m 이내의 첫 좌표면 추가 호출을 생략한다")
    void 가까운_좌표() {
        expectTransit("[[126.978,37.5662697]]");

        assertThat(client.findRoute(ENDPOINTS).steps()).hasSize(1);

        verify(walkClient, never()).findRoute(any());
    }

    @Test
    @DisplayName("30m를 넘으면 도보를 조회한다")
    void 거리_경계_초과() {
        expectTransit("[[126.978,37.5662700]]");

        assertThat(client.findRoute(ENDPOINTS).steps()).hasSize(2);

        verify(walkClient).findRoute(any());
    }

    @Test
    @DisplayName("첫 단계 좌표가 없으면 도보 구간을 추측하지 않는다")
    void 좌표_없음() {
        expectTransit("[]");

        assertThat(client.findRoute(ENDPOINTS).steps()).hasSize(1);

        verify(walkClient, never()).findRoute(any());
    }

    @Test
    @DisplayName("도착 응답이면 도보를 조회하지 않는다")
    void 도착() {
        expectResponse("{\"status\":\"EQUAL_POINTS\"}");

        assertThat(client.findRoute(ENDPOINTS)).isEqualTo(StoreRouteResponse.arrived(RouteMode.TRANSIT));

        verify(walkClient, never()).findRoute(any());
    }

    @Test
    @DisplayName("대중교통 조회 자체가 실패하면 원래 오류를 유지하고 도보를 조회하지 않는다")
    void 대중교통_실패() {
        expectResponse("{\"status\":\"NO_RESULTS\"}");

        assertThatThrownBy(() -> client.findRoute(ENDPOINTS))
                .isInstanceOfSatisfying(GeneralException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(StoreErrorCode.ROUTE_NOT_FOUND));
        verify(walkClient, never()).findRoute(any());
    }

    @Test
    @DisplayName("보조 도보 조회나 응답 해석이 실패해도 대중교통은 정상 반환한다")
    void 도보_실패_폴백() {
        for (RuntimeException failure : List.of(new GeneralException(StoreErrorCode.ROUTE_UNAVAILABLE),
                new GeneralException(StoreErrorCode.ROUTE_NOT_FOUND), new IllegalStateException("invalid response"))) {
            server.reset();
            expectTransit("[[126.978,37.567]]");
            doThrow(failure).when(walkClient).findRoute(any());

            StoreRouteResponse result = client.findRoute(ENDPOINTS);

            assertThat(result.distanceMeters()).isEqualTo(1000);
            assertThat(result.durationSeconds()).isEqualTo(600);
            assertThat(result.steps()).hasSize(1);
        }
    }

    @Test
    @DisplayName("도보가 도착 응답을 주면 합산 없이 대중교통을 반환한다")
    void 도보_빈_경로() {
        expectTransit("[[126.978,37.567]]");
        when(walkClient.findRoute(any())).thenReturn(StoreRouteResponse.arrived(RouteMode.WALK));

        assertThat(client.findRoute(ENDPOINTS).distanceMeters()).isEqualTo(1000);
    }

    private KakaoTransitRouteClient createClient(boolean enabled) {
        KakaoRouteProperties properties = new KakaoRouteProperties("http://kakao.test", "http://kakao.test",
                "test-key", Duration.ofSeconds(2), Duration.ofSeconds(5), enabled);
        return new KakaoTransitRouteClient(restClient, new StoreRouteConverter(), walkClient, properties, calls);
    }

    private void expectTransit(String points) {
        expectResponse("""
                {"status":"OK","routes":[{
                  "properties":{"totalDistance":1000,"totalTime":600,"fare":{"value":1500},"transfers":1},
                  "steps":[{"properties":{"type":"BUS","distance":1000,"time":600,
                    "vehicles":[{"name":"76"}]},"path":{"points":%s}}]
                }]}
                """.formatted(points));
    }

    private void expectResponse(String body) {
        server.expect(requestTo(startsWith("http://kakao.test/v2/routing/publictraffic")))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }
}
