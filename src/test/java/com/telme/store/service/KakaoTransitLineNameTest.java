package com.telme.store.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.telme.global.common.exception.GeneralException;
import com.telme.store.config.KakaoRouteProperties;
import com.telme.store.converter.StoreRouteConverter;
import com.telme.store.dto.req.RouteMode;
import com.telme.store.dto.res.StoreRouteResponse;
import com.telme.store.exception.StoreErrorCode;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KakaoTransitLineNameTest {

    private static final RouteEndpoints ENDPOINTS = new RouteEndpoints(
            new BigDecimal("37.566"), new BigDecimal("126.978"),
            new BigDecimal("37.497942"), new BigDecimal("127.027621"));
    private final KakaoWalkRouteClient walkClient = mock(KakaoWalkRouteClient.class);
    private MockRestServiceServer server;
    private KakaoTransitRouteClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://kakao.test");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new KakaoTransitRouteClient(builder.build(), new StoreRouteConverter(), walkClient,
                new KakaoRouteProperties("http://kakao.test", "http://kakao.test", "test-key",
                        Duration.ofSeconds(2), Duration.ofSeconds(5), true));
    }

    @ParameterizedTest(name = "{0} vehicles={1}")
    @MethodSource("missingLineNames")
    @DisplayName("버스·지하철의 선택된 노선명이 누락되거나 공백이면 503이고 보조 도보도 조회하지 않는다")
    void 탑승_노선명_누락(String type, String vehicles) {
        expectTransit(type, vehicles);

        assertThatThrownBy(() -> client.findRoute(ENDPOINTS))
                .isInstanceOfSatisfying(GeneralException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(StoreErrorCode.ROUTE_UNAVAILABLE));
        verify(walkClient, never()).findRoute(any());
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"WALKING", "FUTURE_TYPE"})
    @DisplayName("걷기와 기존 걷기 폴백 단계는 차량 정보가 없어도 정상이고 노선명은 null이다")
    void 비탑승_단계(String type) {
        expectTransit(type, "");

        StoreRouteResponse result = client.findRoute(ENDPOINTS);

        assertThat(result.steps()).singleElement().satisfies(step -> {
            assertThat(step.type()).isEqualTo(StoreRouteResponse.StepType.WALK);
            assertThat(step.lineName()).isNull();
        });
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"BUS", "SUBWAY"})
    @DisplayName("노선명이 있는 탑승 단계는 기존처럼 첫 번째 노선명을 사용한다")
    void 정상_노선명(String type) {
        String firstName = "BUS".equals(type) ? "76" : "분당선";
        expectTransit(type, "[{\"name\":\"%s\"},{\"name\":\"다른 노선\"}]".formatted(firstName));

        assertThat(client.findRoute(ENDPOINTS).steps()).singleElement()
                .satisfies(step -> assertThat(step.lineName()).isEqualTo(firstName));
        server.verify();
    }

    @Test
    @DisplayName("노선명이 없는 실패 후 다음 요청에서 새 응답으로 다시 조회할 수 있다")
    void 실패_후_재조회() {
        expectTransit("SUBWAY", "[]");
        expectTransit("SUBWAY", "[{\"name\":\"분당선\"}]");
        assertThatThrownBy(() -> client.findRoute(ENDPOINTS)).isInstanceOf(GeneralException.class);
        StoreRouteResponse retried = client.findRoute(ENDPOINTS);

        assertThat(retried.steps().get(0).lineName()).isEqualTo("분당선");
        server.verify();
    }

    private static Stream<Arguments> missingLineNames() {
        return Stream.of("BUS", "SUBWAY").flatMap(type -> Stream.of(
                "", "null", "[]", "[null]", "[{}]", "[{\"name\":null}]", "[{\"name\":\"\"}]",
                "[{\"name\":\"   \"}]", "[{\"name\":\"\\t\\n\"}]",
                "[{\"name\":null},{\"name\":\"76\"}]")
                .map(vehicles -> Arguments.of(type, vehicles)));
    }

    private void expectTransit(String type, String vehicles) {
        String vehicleProperty = vehicles.isEmpty() ? "" : ",\"vehicles\":" + vehicles;
        String body = """
                {"status":"OK","routes":[{
                  "properties":{"totalDistance":1000,"totalTime":600},
                  "steps":[{"properties":{"type":"%s","distance":1000,"time":600%s},
                    "path":{"points":[[126.978,37.566]]}}]
                }]}
                """.formatted(type, vehicleProperty);
        server.expect(requestTo(startsWith("http://kakao.test/v2/routing/publictraffic")))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }
}
