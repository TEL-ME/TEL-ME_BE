package com.telme.store.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.telme.store.config.KakaoRouteProperties;
import com.telme.store.config.StoreRouteProperties;
import com.telme.store.converter.StoreRouteConverter;
import com.telme.store.dto.req.RouteMode;
import com.telme.store.dto.req.StoreRouteRequest;
import com.telme.store.dto.res.StoreRouteResponse;
import com.telme.store.dto.res.StoreRouteResponse.Coordinate;
import com.telme.store.dto.res.StoreRouteResponse.Step;
import com.telme.store.dto.res.StoreRouteResponse.StepType;
import com.telme.store.entity.Store;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KakaoTransitAlternativesTest {

    // 2026-10-23 카카오 대중교통 실제 응답(서울숲 → 구리시청 부근)의 앞 경로 3개. 정류장과 좌표는 단계마다 처음·끝만 남겼다
    private static final String THREE_ROUTES = "/kakao-route/transit-three-routes.json";

    private static final RouteEndpoints ENDPOINTS = new RouteEndpoints(
            new BigDecimal("37.5444"), new BigDecimal("127.0374"),
            new BigDecimal("37.5943"), new BigDecimal("127.1296"));
    private static final Step WALK_STEP = new Step(StepType.WALK, "정류장까지 도보", 120, 100, null,
            List.of(new Coordinate(37.5444, 127.0374), new Coordinate(37.5447, 127.0366)));
    private static final StoreRouteResponse WALK = StoreRouteResponse.builder()
            .mode(RouteMode.WALK).distanceMeters(120).durationSeconds(100).steps(List.of(WALK_STEP)).build();

    private final KakaoRouteCalls calls = new KakaoRouteCalls(mock(StoreRouteUsageGuard.class));
    private final KakaoWalkRouteClient walkClient = mock(KakaoWalkRouteClient.class);
    private MockRestServiceServer server;
    private RestClient restClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://kakao.test");
        server = MockRestServiceServer.bindTo(builder).build();
        restClient = builder.build();
        when(walkClient.findRoute(any())).thenReturn(WALK);
    }

    @Test
    @DisplayName("카카오가 추천 순으로 준 경로 중 첫 경로를 응답 값으로, 나머지를 같은 순서로 alternatives에 담는다")
    void 추천_순서대로_대체_경로를_담는다() throws IOException {
        expectTransit(readSample());

        StoreRouteResponse result = createClient(true).findRoute(ENDPOINTS);

        assertThat(result.mode()).isEqualTo(RouteMode.TRANSIT);
        assertThat(result.distanceMeters()).isEqualTo(14863 + 120);
        assertThat(result.durationSeconds()).isEqualTo(3875 + 100);
        assertThat(result.fare()).isEqualTo(1750);
        assertThat(result.originWalkIncluded()).isTrue();
        assertThat(result.steps()).extracting(Step::type).containsExactly(StepType.WALK, StepType.BUS, StepType.BUS);
        assertThat(result.alternatives()).extracting(StoreRouteResponse::distanceMeters).containsExactly(17201, 16959);
        assertThat(result.alternatives()).extracting(StoreRouteResponse::durationSeconds).containsExactly(3296, 3515);
        assertThat(result.alternatives()).allSatisfy(alternative -> {
            assertThat(alternative.mode()).isEqualTo(RouteMode.TRANSIT);
            assertThat(alternative.originWalkIncluded()).isFalse();
            assertThat(alternative.alternatives()).isEmpty();
            assertThat(alternative.steps().get(0).type()).isEqualTo(StepType.BUS);
        });
        assertThat(result.alternatives().get(0).steps().get(1).guidance()).isEqualTo("왕십리역까지 도보로 이동 (12번 출구)");
        server.verify();
    }

    @Test
    @DisplayName("출발지 도보 보강은 첫 경로에만 붙여 도보 API를 요청마다 한 번만 부른다")
    void 도보_보강은_첫_경로만() throws IOException {
        expectTransit(readSample());

        createClient(true).findRoute(ENDPOINTS);

        verify(walkClient, times(1)).findRoute(any());
    }

    @Test
    @DisplayName("해석할 수 없는 대체 경로는 빼고, 나머지 경로는 순서를 유지해 내린다")
    void 해석_못_한_대체_경로는_뺀다() throws IOException {
        // 두 번째 경로의 첫 버스 단계에서 노선명을 지워 해석할 수 없게 만든다
        expectTransit(readSample().replaceFirst("\"name\": \"145\"", "\"name\": \"\""));

        StoreRouteResponse result = createClient(true).findRoute(ENDPOINTS);

        assertThat(result.distanceMeters()).isEqualTo(14863 + 120);
        assertThat(result.alternatives()).extracting(StoreRouteResponse::distanceMeters).containsExactly(16959);
    }

    @Test
    @DisplayName("경로가 하나뿐이면 alternatives는 비어 있고, 도보 보강을 끄면 originWalkIncluded는 false다")
    void 경로가_하나면_대체_경로가_없다() {
        expectTransit("""
                {"status":"OK","routes":[{
                  "properties":{"totalDistance":1000,"totalTime":600,"fare":{"value":1500},"transfers":0},
                  "steps":[{"properties":{"type":"BUS","distance":1000,"time":600,
                    "vehicles":[{"name":"76"}]},"path":{"points":[[127.0366,37.5447],[127.04,37.55]]}}]
                }]}
                """);

        StoreRouteResponse result = createClient(false).findRoute(ENDPOINTS);

        assertThat(result.alternatives()).isEmpty();
        assertThat(result.originWalkIncluded()).isFalse();
        verify(walkClient, never()).findRoute(any());
    }

    @Test
    @DisplayName("최대 거리를 넘는 대체 경로는 빼고, 첫 경로가 한도 안이면 그대로 응답한다")
    void 최대_거리를_넘는_대체_경로는_뺀다() {
        StoreRouteClient transitClient = mockClient(RouteMode.TRANSIT);
        StoreRouteResponse first = new StoreRouteResponse(RouteMode.TRANSIT, 15000, 3000, 1500, 0, List.of(), true,
                List.of(new StoreRouteResponse(RouteMode.TRANSIT, 17000, 3200, 1500, 1, List.of(), false, List.of()),
                        new StoreRouteResponse(RouteMode.TRANSIT, 15500, 3100, 1500, 1, List.of(), false, List.of())));
        when(transitClient.findRoute(any())).thenReturn(first);
        StoreRouteService service = createService(transitClient, 16000);

        StoreRouteResponse result = service.getRoute(7L, new StoreRouteRequest(RouteMode.TRANSIT, 37.5444, 127.0374));

        assertThat(result.distanceMeters()).isEqualTo(15000);
        assertThat(result.originWalkIncluded()).isTrue();
        assertThat(result.alternatives()).extracting(StoreRouteResponse::distanceMeters).containsExactly(15500);
    }

    @Test
    @DisplayName("대중교통이 아닌 수단은 alternatives가 비어 있고 originWalkIncluded가 없다")
    void 다른_수단은_대체_경로가_없다() {
        StoreRouteResponse walk = new StoreRouteResponse(RouteMode.WALK, 120, 100, null, null, List.of(WALK_STEP));

        assertThat(walk.alternatives()).isEmpty();
        assertThat(walk.originWalkIncluded()).isNull();
        assertThat(StoreRouteResponse.arrived(RouteMode.WALK).originWalkIncluded()).isNull();
    }

    @Test
    @DisplayName("대중교통에서 출발지와 도착지가 같으면 도보 보강을 하지 않았으므로 originWalkIncluded는 false다")
    void 대중교통_도착_응답은_도보_보강_false() {
        StoreRouteResponse arrived = StoreRouteResponse.arrived(RouteMode.TRANSIT);

        assertThat(arrived.originWalkIncluded()).isFalse();
        assertThat(arrived.alternatives()).isEmpty();
    }

    private KakaoTransitRouteClient createClient(boolean originWalkEnabled) {
        KakaoRouteProperties properties = new KakaoRouteProperties("http://kakao.test", "http://kakao.test",
                "test-key", Duration.ofSeconds(2), Duration.ofSeconds(5), originWalkEnabled);
        return new KakaoTransitRouteClient(restClient, new StoreRouteConverter(), walkClient, properties, calls);
    }

    private StoreRouteService createService(StoreRouteClient transitClient, int maxDistanceMeters) {
        StoreQueryService storeQueryService = mock(StoreQueryService.class);
        when(storeQueryService.getOpenStore(7L)).thenReturn(Store.builder()
                .storeId(7L)
                .latitude(new BigDecimal("37.5943"))
                .longitude(new BigDecimal("127.1296"))
                .build());
        return new StoreRouteService(storeQueryService,
                List.of(mockClient(RouteMode.CAR), transitClient, mockClient(RouteMode.WALK), mockClient(RouteMode.BICYCLE)),
                new StoreRouteProperties(maxDistanceMeters), mock(StoreRouteUsageGuard.class),
                mock(StoreOriginWalkGuard.class));
    }

    private static StoreRouteClient mockClient(RouteMode mode) {
        StoreRouteClient client = mock(StoreRouteClient.class);
        when(client.mode()).thenReturn(mode);
        return client;
    }

    private void expectTransit(String body) {
        server.expect(requestTo(startsWith("http://kakao.test/v2/routing/publictraffic")))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private String readSample() throws IOException {
        try (InputStream in = getClass().getResourceAsStream(THREE_ROUTES)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
