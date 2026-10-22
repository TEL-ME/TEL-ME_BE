package com.telme.store.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KakaoRouteResponseValidationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final RouteEndpoints ENDPOINTS = new RouteEndpoints(new BigDecimal("37.566"),
            new BigDecimal("126.978"), new BigDecimal("37.497942"), new BigDecimal("127.027621"));
    private MockRestServiceServer server;
    private Map<RouteMode, StoreRouteClient> clients;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder().baseUrl("http://kakao.test");
        server = MockRestServiceServer.bindTo(builder).build();
        var rest = builder.build();
        var converter = new StoreRouteConverter();
        var calls = new KakaoRouteCalls(mock(StoreRouteUsageGuard.class));
        var walk = new KakaoWalkRouteClient(rest, converter, calls);
        clients = Map.of(RouteMode.CAR, new KakaoNaviCarRouteClient(rest, converter, calls),
                RouteMode.TRANSIT, new KakaoTransitRouteClient(rest, converter, walk,
                        new KakaoRouteProperties("http://kakao.test", "http://kakao.test", "test-key",
                                Duration.ofSeconds(2), Duration.ofSeconds(5), false), calls),
                RouteMode.WALK, walk, RouteMode.BICYCLE, new KakaoBicycleRouteClient(rest, converter, calls));
    }

    @ParameterizedTest(name = "{0} {1} = {2}")
    @MethodSource("invalidStructures")
    void 잘못된_외부_구조는_NPE가_아닌_연동_오류(RouteMode mode, String pointer, String value) throws Exception {
        JsonNode body = MAPPER.readTree(body(mode));
        int separator = pointer.lastIndexOf('/');
        JsonNode parent = body.at(pointer.substring(0, separator));
        String key = pointer.substring(separator + 1);
        if (parent instanceof ArrayNode array) {
            array.set(Integer.parseInt(key), MAPPER.readTree(value));
        } else {
            ((ObjectNode) parent).set(key, MAPPER.readTree(value));
        }
        server.expect(requestTo(startsWith("http://kakao.test")))
                .andRespond(withSuccess(body.toString(), MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> clients.get(mode).findRoute(ENDPOINTS))
                .isInstanceOfSatisfying(GeneralException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(StoreErrorCode.ROUTE_UNAVAILABLE));
        server.verify();
    }

    @Test
    void 보강_합산_오버플로도_음수로_반환하지_않음() {
        var converter = new StoreRouteConverter();
        var transit = StoreRouteResponse.builder().mode(RouteMode.TRANSIT).distanceMeters(Integer.MAX_VALUE)
                .durationSeconds(10).steps(List.of()).build();
        var walk = StoreRouteResponse.builder().mode(RouteMode.WALK).distanceMeters(1)
                .durationSeconds(10).steps(List.of()).build();
        assertThatThrownBy(() -> converter.prependOriginWalk(transit, walk))
                .isInstanceOfSatisfying(GeneralException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(StoreErrorCode.ROUTE_UNAVAILABLE));
    }

    private static Stream<Arguments> invalidStructures() {
        var cases = new java.util.ArrayList<Arguments>();
        for (RouteMode mode : RouteMode.values()) {
            if (mode == RouteMode.CAR) {
                add(cases, mode, "/routes/0", "null");
                add(cases, mode, "/routes/0/summary", "null");
                add(cases, mode, "/routes/0/summary/distance", "-1");
                add(cases, mode, "/routes/0/summary/duration", "-1");
                add(cases, mode, "/routes/0/sections/0", "null");
                add(cases, mode, "/routes/0/sections/0/roads/0", "null");
                for (String vertices : List.of("[null,37.5]", "[127,null]", "[127]", "[181,37]")) {
                    add(cases, mode, "/routes/0/sections/0/roads/0/vertexes", vertices);
                }
            } else {
                String route = mode == RouteMode.TRANSIT ? "/routes/0" : "/route";
                String step = route + (mode == RouteMode.TRANSIT ? "/steps/0" : "/legs/0/steps/0");
                add(cases, mode, route, "null");
                add(cases, mode, route + "/properties", "null");
                add(cases, mode, route + "/properties/totalDistance", "-1");
                add(cases, mode, route + "/properties/totalTime", "-1");
                add(cases, mode, step, "null");
                add(cases, mode, step + "/properties", "null");
                add(cases, mode, step + "/properties/distance", "-1");
                add(cases, mode, step + "/properties/time", "-1");
                for (String points : List.of("[null]", "[[]]", "[[127]]", "[[null,37.5]]",
                        "[[127,null]]", "[[127,91]]", "[[\"NaN\",37]]")) {
                    add(cases, mode, step + "/path/points", points);
                }
                if (mode != RouteMode.TRANSIT) {
                    add(cases, mode, route + "/legs/0", "null");
                }
            }
        }
        return cases.stream();
    }

    private static void add(List<Arguments> cases, RouteMode mode, String pointer, String value) {
        cases.add(Arguments.of(mode, pointer, value));
    }

    private static String body(RouteMode mode) {
        if (mode == RouteMode.CAR) {
            return """
                    {"routes":[{"result_code":0,"summary":{"distance":1000,"duration":600},
                    "sections":[{"roads":[{"vertexes":[127,37.5]}]}]}]}
                    """;
        }
        String step = """
                {"properties":{"type":"BUS","distance":1000,"time":600,"vehicles":[{"name":"76"}]},
                "path":{"points":[[127,37.5]]}}
                """;
        if (mode == RouteMode.TRANSIT) {
            return """
                    {"status":"OK","routes":[{"properties":{"totalDistance":1000,"totalTime":600},
                    "steps":[%s]}]}
                    """.formatted(step);
        }
        return """
                {"status":"OK","route":{"properties":{"totalDistance":1000,"totalTime":600},
                "legs":[{"steps":[%s]}]}}
                """.formatted(step);
    }
}
