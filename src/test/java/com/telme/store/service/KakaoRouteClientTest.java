package com.telme.store.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.telme.global.common.exception.GeneralException;
import com.telme.store.config.KakaoRouteProperties;
import com.telme.store.converter.StoreRouteConverter;
import com.telme.store.dto.req.RouteMode;
import com.telme.store.dto.res.StoreRouteResponse;
import com.telme.store.exception.StoreErrorCode;
import java.io.IOException;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

// 실제 카카오 API는 호출하지 않는다. 응답 예시는 카카오맵·카카오모빌리티 길찾기 문서의 형식을 따른다.
// 출발지는 서울 시청 근처, 도착지는 강남역 근처 좌표다
class KakaoRouteClientTest {

    private final KakaoRouteCalls calls = new KakaoRouteCalls(mock(StoreRouteUsageGuard.class));

    private static final String BASE_URL = "http://kakao.test";
    private static final String AUTH = "KakaoAK test-key";
    private static final RouteEndpoints ENDPOINTS = new RouteEndpoints(
            new BigDecimal("37.5663"), new BigDecimal("126.9779"),
            new BigDecimal("37.497942"), new BigDecimal("127.027621"));

    private MockRestServiceServer server;
    private KakaoNaviCarRouteClient carClient;
    private KakaoTransitRouteClient transitClient;
    private KakaoWalkRouteClient walkClient;
    private KakaoBicycleRouteClient bicycleClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader(HttpHeaders.AUTHORIZATION, AUTH);
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        StoreRouteConverter converter = new StoreRouteConverter();
        carClient = new KakaoNaviCarRouteClient(restClient, converter, calls);
        transitClient = new KakaoTransitRouteClient(restClient, converter,
                new KakaoWalkRouteClient(restClient, converter, calls),
                new KakaoRouteProperties(BASE_URL, BASE_URL, "test-key",
                        java.time.Duration.ofSeconds(2), java.time.Duration.ofSeconds(5), false), calls);
        walkClient = new KakaoWalkRouteClient(restClient, converter, calls);
        bicycleClient = new KakaoBicycleRouteClient(restClient, converter, calls);
    }

    @Test
    @DisplayName("자동차는 x,y 순서로 출발지·도착지를 보내고 vertexes를 위도·경도 좌표로 바꾼다")
    void 자동차_성공() {
        server.expect(requestTo(startsWith(BASE_URL + "/v1/directions")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, AUTH))
                .andExpect(queryParam("origin", "126.9779,37.5663"))
                .andExpect(queryParam("destination", "127.027621,37.497942"))
                .andRespond(withSuccess(CAR_OK, MediaType.APPLICATION_JSON));

        StoreRouteResponse response = carClient.findRoute(ENDPOINTS);

        assertThat(response.mode()).isEqualTo(RouteMode.CAR);
        assertThat(response.distanceMeters()).isEqualTo(11250);
        assertThat(response.durationSeconds()).isEqualTo(1620);
        assertThat(response.steps()).singleElement().satisfies(step -> {
            assertThat(step.type()).isEqualTo(StoreRouteResponse.StepType.DRIVE);
            assertThat(step.path()).containsExactly(
                    new StoreRouteResponse.Coordinate(37.5663, 126.9779),
                    new StoreRouteResponse.Coordinate(37.5, 127.0),
                    new StoreRouteResponse.Coordinate(37.497942, 127.027621));
        });
        server.verify();
    }

    @Test
    @DisplayName("자동차는 출발지와 도착지가 5m 이내(104)면 도착한 응답을 돌려준다")
    void 자동차_같은_지점() {
        server.expect(requestTo(startsWith(BASE_URL + "/v1/directions")))
                .andRespond(withSuccess(carResult(104, "출발지와 도착지가 5m 이내"), MediaType.APPLICATION_JSON));

        StoreRouteResponse response = carClient.findRoute(ENDPOINTS);

        assertThat(response).isEqualTo(StoreRouteResponse.arrived(RouteMode.CAR));
    }

    @Test
    @DisplayName("자동차는 도로를 못 찾거나(102·103) 통행 장애(105)가 있으면 경로 없음이다")
    void 자동차_경로_없음() {
        for (int resultCode : new int[] {1, 102, 103, 105}) {
            server.reset();
            server.expect(requestTo(startsWith(BASE_URL + "/v1/directions")))
                    .andRespond(withSuccess(carResult(resultCode, "실패"), MediaType.APPLICATION_JSON));

            assertThatThrownBy(() -> carClient.findRoute(ENDPOINTS))
                    .isInstanceOfSatisfying(GeneralException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(StoreErrorCode.ROUTE_NOT_FOUND));
        }
    }

    @Test
    @DisplayName("자동차는 모르는 result_code면 연동 오류로 본다")
    void 자동차_모르는_코드() {
        server.expect(requestTo(startsWith(BASE_URL + "/v1/directions")))
                .andRespond(withSuccess(carResult(999, "?"), MediaType.APPLICATION_JSON));

        assertUnavailable(() -> carClient.findRoute(ENDPOINTS));
    }

    @Test
    @DisplayName("키 오류(401)·호출 한도 초과(429)·서버 오류·타임아웃은 모두 길찾기 연결 실패로 알린다")
    void 호출_실패() {
        for (HttpStatus status : new HttpStatus[] {HttpStatus.UNAUTHORIZED, HttpStatus.TOO_MANY_REQUESTS,
                HttpStatus.BAD_REQUEST}) {
            server.reset();
            server.expect(requestTo(startsWith(BASE_URL + "/v1/directions")))
                    .andRespond(withStatus(status).body("{\"code\":-10}").contentType(MediaType.APPLICATION_JSON));
            assertUnavailable(() -> carClient.findRoute(ENDPOINTS));
        }

        server.reset();
        server.expect(requestTo(startsWith(BASE_URL + "/v1/directions"))).andRespond(withServerError());
        assertUnavailable(() -> carClient.findRoute(ENDPOINTS));

        server.reset();
        server.expect(requestTo(startsWith(BASE_URL + "/v1/directions")))
                .andRespond(withException(new IOException("read timed out")));
        assertUnavailable(() -> carClient.findRoute(ENDPOINTS));
    }

    @Test
    @DisplayName("대중교통은 첫 경로의 요금·환승·단계별 종류와 노선 이름을 읽는다")
    void 대중교통_성공() {
        server.expect(requestTo(startsWith(BASE_URL + "/v2/routing/publictraffic")))
                .andExpect(header(HttpHeaders.AUTHORIZATION, AUTH))
                .andExpect(queryParam("start_x", "126.9779"))
                .andExpect(queryParam("start_y", "37.5663"))
                .andExpect(queryParam("end_x", "127.027621"))
                .andExpect(queryParam("end_y", "37.497942"))
                .andRespond(withSuccess(TRANSIT_OK, MediaType.APPLICATION_JSON));

        StoreRouteResponse response = transitClient.findRoute(ENDPOINTS);

        assertThat(response.mode()).isEqualTo(RouteMode.TRANSIT);
        assertThat(response.distanceMeters()).isEqualTo(5013);
        assertThat(response.durationSeconds()).isEqualTo(2115);
        assertThat(response.fare()).isEqualTo(1350);
        assertThat(response.transfers()).isEqualTo(1);
        assertThat(response.steps()).hasSize(3);
        assertThat(response.steps().get(0).type()).isEqualTo(StoreRouteResponse.StepType.WALK);
        assertThat(response.steps().get(0).lineName()).isNull();
        assertThat(response.steps().get(1).type()).isEqualTo(StoreRouteResponse.StepType.BUS);
        assertThat(response.steps().get(1).lineName()).isEqualTo("76");
        assertThat(response.steps().get(1).path()).containsExactly(
                new StoreRouteResponse.Coordinate(37.3948816, 127.11316378),
                new StoreRouteResponse.Coordinate(37.39488161, 127.11312974));
        assertThat(response.steps().get(2).type()).isEqualTo(StoreRouteResponse.StepType.SUBWAY);
        assertThat(response.steps().get(2).lineName()).isEqualTo("분당선");
    }

    @Test
    @DisplayName("대중교통은 출발지와 도착지가 같으면(EQUAL_POINTS) 도착한 응답이다")
    void 대중교통_같은_지점() {
        server.expect(requestTo(startsWith(BASE_URL + "/v2/routing/publictraffic")))
                .andRespond(withSuccess("{\"status\":\"EQUAL_POINTS\"}", MediaType.APPLICATION_JSON));

        assertThat(transitClient.findRoute(ENDPOINTS)).isEqualTo(StoreRouteResponse.arrived(RouteMode.TRANSIT));
    }

    @Test
    @DisplayName("대중교통은 경로가 없거나(NO_RESULTS) 출발·도착 노드가 없으면 경로 없음이다")
    void 대중교통_경로_없음() {
        for (String status : new String[] {"NO_RESULTS", "STARTNODES_NULL", "ENDNODES_NULL"}) {
            server.reset();
            server.expect(requestTo(startsWith(BASE_URL + "/v2/routing/publictraffic")))
                    .andRespond(withSuccess("{\"status\":\"" + status + "\"}", MediaType.APPLICATION_JSON));

            assertThatThrownBy(() -> transitClient.findRoute(ENDPOINTS))
                    .isInstanceOfSatisfying(GeneralException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(StoreErrorCode.ROUTE_NOT_FOUND));
        }
    }

    @Test
    @DisplayName("대중교통은 INVALID_REQUEST처럼 우리가 잘못 보낸 요청은 연동 오류로 본다")
    void 대중교통_잘못된_요청() {
        server.expect(requestTo(startsWith(BASE_URL + "/v2/routing/publictraffic")))
                .andRespond(withSuccess("{\"status\":\"INVALID_REQUEST\"}", MediaType.APPLICATION_JSON));

        assertUnavailable(() -> transitClient.findRoute(ENDPOINTS));
    }

    @Test
    @DisplayName("도보는 단계 안내 문구와 좌표를 읽고 단계 종류는 WALK다")
    void 도보_성공() {
        server.expect(requestTo(startsWith(BASE_URL + "/v2/routing/walk")))
                .andExpect(queryParam("start_x", "126.9779"))
                .andExpect(queryParam("end_y", "37.497942"))
                .andRespond(withSuccess(LEG_OK, MediaType.APPLICATION_JSON));

        StoreRouteResponse response = walkClient.findRoute(ENDPOINTS);

        assertThat(response.mode()).isEqualTo(RouteMode.WALK);
        assertThat(response.distanceMeters()).isEqualTo(4025);
        assertThat(response.durationSeconds()).isEqualTo(3914);
        assertThat(response.fare()).isNull();
        assertThat(response.steps()).singleElement().satisfies(step -> {
            assertThat(step.type()).isEqualTo(StoreRouteResponse.StepType.WALK);
            assertThat(step.guidance()).isEqualTo("판교역 1번 출구까지 역사 내 이동");
            assertThat(step.path()).containsExactly(
                    new StoreRouteResponse.Coordinate(37.39477573, 127.11119783),
                    new StoreRouteResponse.Coordinate(37.39474869, 127.11119779));
        });
    }

    @Test
    @DisplayName("자전거는 자전거 경로 주소를 부르고 단계 종류는 BICYCLE이다")
    void 자전거_성공() {
        server.expect(requestTo(startsWith(BASE_URL + "/v2/routing/bicycle")))
                .andRespond(withSuccess(LEG_OK, MediaType.APPLICATION_JSON));

        StoreRouteResponse response = bicycleClient.findRoute(ENDPOINTS);

        assertThat(response.mode()).isEqualTo(RouteMode.BICYCLE);
        assertThat(response.steps()).singleElement()
                .satisfies(step -> assertThat(step.type()).isEqualTo(StoreRouteResponse.StepType.BICYCLE));
    }

    @Test
    @DisplayName("도보는 너무 멀거나(TOO_FAR_AWAY) 주변 도로가 없으면 경로 없음이고, 같은 지점이면 도착한 응답이다")
    void 도보_실패_상태() {
        for (String status : new String[] {"TOO_FAR_AWAY", "START_LINK_NOT_FOUND", "END_LINK_NOT_FOUND",
                "ROUTE_RESULT_NOT_FOUND", "TOO_MANY_SEARCH_LINK"}) {
            server.reset();
            server.expect(requestTo(startsWith(BASE_URL + "/v2/routing/walk")))
                    .andRespond(withSuccess("{\"status\":\"" + status + "\"}", MediaType.APPLICATION_JSON));

            assertThatThrownBy(() -> walkClient.findRoute(ENDPOINTS))
                    .isInstanceOfSatisfying(GeneralException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(StoreErrorCode.ROUTE_NOT_FOUND));
        }

        server.reset();
        server.expect(requestTo(startsWith(BASE_URL + "/v2/routing/walk")))
                .andRespond(withSuccess("{\"status\":\"SAME_POINT\"}", MediaType.APPLICATION_JSON));
        assertThat(walkClient.findRoute(ENDPOINTS)).isEqualTo(StoreRouteResponse.arrived(RouteMode.WALK));
    }

    private void assertUnavailable(Runnable call) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(GeneralException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(StoreErrorCode.ROUTE_UNAVAILABLE));
    }

    private static String carResult(int resultCode, String message) {
        return "{\"trans_id\":\"t\",\"routes\":[{\"result_code\":" + resultCode
                + ",\"result_msg\":\"" + message + "\"}]}";
    }

    private static final String CAR_OK = """
            {"trans_id":"t","routes":[{"result_code":0,"result_msg":"길찾기 성공",
              "summary":{"distance":11250,"duration":1620,"fare":{"taxi":15000,"toll":0}},
              "sections":[{"distance":11250,"duration":1620,
                "roads":[{"name":"세종대로","distance":100,"duration":20,
                          "vertexes":[126.9779,37.5663,127.0,37.5]},
                         {"name":"테헤란로","distance":50,"duration":10,
                          "vertexes":[127.027621,37.497942]}],
                "guides":[]}]}]}
            """;

    private static final String TRANSIT_OK = """
            {"status":"OK",
             "properties":{"total":2,"bus":1,"subway":0,"busAndSubway":1,"landingURL":"https://map.kakao.com"},
             "routes":[{
               "properties":{"type":"BUS_AND_SUBWAY","totalDistance":5013,"totalTime":2115,"transfers":1,
                             "fare":{"value":1350}},
               "steps":[
                 {"properties":{"guidance":"출발지에서 걷기","type":"WALKING","distance":80,"time":60},
                  "path":{"points":[[127.11119217,37.39477123],[127.11316378,37.3948816]]}},
                 {"properties":{"guidance":"마을 76 (판교역동편 > 성남시청전면)","type":"BUS","distance":4127,"time":1158,
                                "stops":[{"name":"판교역동편"}],"vehicles":[{"name":"76","type":"마을"}]},
                  "path":{"points":[[127.11316378,37.3948816],[127.11312974,37.39488161]]}},
                 {"properties":{"guidance":"분당선","type":"SUBWAY","distance":800,"time":300,
                                "vehicles":[{"name":"분당선","type":"일반"}]},
                  "path":{"points":[[127.1,37.4]]}}]
             }]}
            """;

    private static final String LEG_OK = """
            {"status":"OK",
             "route":{"properties":{"totalDistance":4025,"totalTime":3914,"landingUrl":"https://map.kakao.com"},
               "legs":[{"properties":{"distance":4025,"time":3914},
                 "steps":[{"properties":{"distance":93,"guidance":"판교역 1번 출구까지 역사 내 이동","time":84,
                                         "x":127.11119783,"y":37.39477573},
                           "path":{"points":[[127.11119783,37.39477573],[127.11119779,37.39474869]]}}]}]}}
            """;
}
