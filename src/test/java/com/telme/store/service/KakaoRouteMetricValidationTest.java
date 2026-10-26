package com.telme.store.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.telme.global.common.exception.GeneralException;
import com.telme.store.config.KakaoRouteProperties;
import com.telme.store.converter.StoreRouteConverter;
import com.telme.store.dto.req.RouteMode;
import com.telme.store.dto.res.StoreRouteResponse;
import com.telme.store.exception.StoreErrorCode;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KakaoRouteMetricValidationTest {

    private final KakaoRouteCalls calls = new KakaoRouteCalls(mock(StoreRouteUsageGuard.class));

    private static final RouteEndpoints ENDPOINTS = new RouteEndpoints(
            new BigDecimal("37.566"), new BigDecimal("126.978"),
            new BigDecimal("37.497942"), new BigDecimal("127.027621"));
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private MockRestServiceServer server;
    private Map<RouteMode, StoreRouteClient> clients;
    private RestClient restClient;
    private KakaoWalkRouteClient walkClient;
    private StoreRouteConverter converter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://kakao.test");
        server = MockRestServiceServer.bindTo(builder).build();
        restClient = builder.build();
        converter = new StoreRouteConverter();
        walkClient = new KakaoWalkRouteClient(restClient, converter, calls);
        clients = Map.of(
                RouteMode.CAR, new KakaoNaviCarRouteClient(restClient, converter, calls),
                RouteMode.TRANSIT, new KakaoTransitRouteClient(restClient, converter, walkClient,
                        properties(false), calls),
                RouteMode.WALK, walkClient,
                RouteMode.BICYCLE, new KakaoBicycleRouteClient(restClient, converter, calls));
    }

    @ParameterizedTest(name = "{0} {1} explicitNull={2}")
    @MethodSource("missingMetrics")
    @DisplayName("전체 또는 단계별 거리·시간이 null이거나 누락되면 0으로 바꾸지 않고 503으로 처리한다")
    void 측정값_누락(RouteMode mode, String pointer, boolean explicitNull) throws Exception {
        JsonNode body = MAPPER.readTree(responseBody(mode));
        replaceMetric(body, pointer, explicitNull);
        expectResponse(mode, body.toString());

        assertThatThrownBy(() -> clients.get(mode).findRoute(ENDPOINTS))
                .isInstanceOfSatisfying(GeneralException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(StoreErrorCode.ROUTE_UNAVAILABLE));
        server.verify();
    }

    @ParameterizedTest
    @EnumSource(RouteMode.class)
    @DisplayName("명시적으로 받은 0은 유효한 거리·시간으로 유지한다")
    void 명시적인_영(RouteMode mode) throws Exception {
        JsonNode body = MAPPER.readTree(responseBody(mode));
        for (String pointer : metricPointers(mode)) {
            ObjectNode parent = (ObjectNode) body.at(pointer.substring(0, pointer.lastIndexOf('/')));
            parent.put(pointer.substring(pointer.lastIndexOf('/') + 1), 0);
        }
        expectResponse(mode, body.toString());

        StoreRouteResponse result = clients.get(mode).findRoute(ENDPOINTS);

        assertThat(result.distanceMeters()).isZero();
        assertThat(result.durationSeconds()).isZero();
        assertThat(result.steps()).hasSize(1);
        assertThat(result.steps().get(0).distanceMeters()).isZero();
        assertThat(result.steps().get(0).durationSeconds()).isZero();
        server.verify();
    }

    @Test
    @DisplayName("보조 도보 응답의 측정값 누락은 대중교통 전체를 실패시키지 않는다")
    void 도보_누락은_보강만_생략() throws Exception {
        expectResponse(RouteMode.TRANSIT, responseBody(RouteMode.TRANSIT));
        JsonNode body = MAPPER.readTree(responseBody(RouteMode.WALK));
        replaceMetric(body, "/route/properties/totalDistance", true);
        expectResponse(RouteMode.WALK, body.toString());
        StoreRouteClient transit =
                new KakaoTransitRouteClient(restClient, converter, walkClient, properties(true), calls);

        StoreRouteResponse result = transit.findRoute(ENDPOINTS);

        assertThat(result.mode()).isEqualTo(RouteMode.TRANSIT);
        assertThat(result.distanceMeters()).isEqualTo(1000);
        assertThat(result.steps()).hasSize(1);
        server.verify();
    }

    private static Stream<Arguments> missingMetrics() {
        return Stream.of(RouteMode.values()).flatMap(mode -> metricPointers(mode).stream()
                .flatMap(pointer -> Stream.of(true, false)
                        .map(explicitNull -> Arguments.of(mode, pointer, explicitNull))));
    }

    private static List<String> metricPointers(RouteMode mode) {
        if (mode == RouteMode.CAR) {
            return List.of("/routes/0/summary/distance", "/routes/0/summary/duration");
        }
        String route = mode == RouteMode.TRANSIT ? "/routes/0" : "/route";
        String step = mode == RouteMode.TRANSIT ? "/steps/0/properties/" : "/legs/0/steps/0/properties/";
        return List.of(route + "/properties/totalDistance", route + "/properties/totalTime",
                route + step + "distance", route + step + "time");
    }

    private static void replaceMetric(JsonNode body, String pointer, boolean explicitNull) {
        ObjectNode parent = (ObjectNode) body.at(pointer.substring(0, pointer.lastIndexOf('/')));
        String field = pointer.substring(pointer.lastIndexOf('/') + 1);
        if (explicitNull) {
            parent.putNull(field);
        } else {
            parent.remove(field);
        }
    }

    private void expectResponse(RouteMode mode, String body) {
        String path = switch (mode) {
            case CAR -> "/v1/directions";
            case TRANSIT -> "/v2/routing/publictraffic";
            case WALK -> "/v2/routing/walk";
            case BICYCLE -> "/v2/routing/bicycle";
        };
        server.expect(requestTo(startsWith("http://kakao.test" + path)))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private static KakaoRouteProperties properties(boolean enabled) {
        return new KakaoRouteProperties("http://kakao.test", "http://kakao.test", "test-key",
                Duration.ofSeconds(2), Duration.ofSeconds(5), enabled);
    }

    private static String responseBody(RouteMode mode) {
        if (mode == RouteMode.CAR) {
            return """
                    {"routes":[{"result_code":0,"summary":{"distance":1000,"duration":600},
                    "sections":[{"roads":[{"vertexes":[126.978,37.567]}]}]}]}
                    """;
        }
        String step = """
                {"properties":{"type":"BUS","distance":1000,"time":600,"vehicles":[{"name":"76"}]},
                 "path":{"points":[[126.978,37.567]]}}
                """;
        if (mode == RouteMode.TRANSIT) {
            return """
                    {"status":"OK","routes":[{"properties":{"totalDistance":1000,"totalTime":600},"steps":[%s]}]}
                    """.formatted(step);
        }
        return """
                {"status":"OK","route":{"properties":{"totalDistance":1000,"totalTime":600},
                 "legs":[{"steps":[%s]}]}}
                """.formatted(step);
    }
}
