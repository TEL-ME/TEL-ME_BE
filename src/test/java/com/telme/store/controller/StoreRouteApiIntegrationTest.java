package com.telme.store.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.telme.global.common.exception.GeneralException;
import com.telme.store.config.StoreRouteCacheProperties;
import com.telme.store.config.StoreRouteProperties;
import com.telme.store.dto.req.RouteMode;
import com.telme.store.dto.res.StoreRouteResponse;
import com.telme.store.exception.StoreErrorCode;
import com.telme.store.service.RouteEndpoints;
import com.telme.store.service.StoreQueryService;
import com.telme.store.service.StoreRouteCache;
import com.telme.store.service.StoreRouteClient;
import com.telme.store.service.StoreRouteService;
import java.time.Clock;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

// 시드 매장(dev-migration)으로 길찾기 API를 확인한다. 101번은 영업 중, 157번은 폐점한 시드 매장이다.
// 실제 카카오는 부르지 않는다. 경로 클라이언트 4개를 대신하는 가짜를 쓰는 서비스를 @Primary로 끼워 넣는다
@SpringBootTest(properties = "store.route.max-distance-meters=20000")
@AutoConfigureMockMvc
@Transactional
class StoreRouteApiIntegrationTest {

    private static final StubClient CAR = new StubClient(RouteMode.CAR);
    private static final StubClient WALK = new StubClient(RouteMode.WALK);
    private static final StubClient BICYCLE = new StubClient(RouteMode.BICYCLE);

    @Autowired MockMvc mvc;

    @BeforeEach
    void resetStubs() {
        CAR.reset();
        WALK.reset();
        BICYCLE.reset();
    }

    @Test
    @DisplayName("로그인 없이 이동 수단과 출발지를 보내면 경로를 돌려준다")
    void 경로_조회() throws Exception {
        CAR.respondWith(() -> new StoreRouteResponse(RouteMode.CAR, 11250, 1620, null, null,
                List.of(new StoreRouteResponse.Step(StoreRouteResponse.StepType.DRIVE, null, 11250, 1620, null,
                        List.of(new StoreRouteResponse.Coordinate(37.5663, 126.9779))))));

        mvc.perform(route(101, "CAR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.mode").value("CAR"))
                .andExpect(jsonPath("$.result.distanceMeters").value(11250))
                .andExpect(jsonPath("$.result.steps[0].type").value("DRIVE"))
                .andExpect(jsonPath("$.result.steps[0].path[0].latitude").value(37.5663))
                .andExpect(jsonPath("$.result.steps[0].path[0].longitude").value(126.9779));
    }

    @Test
    @DisplayName("없거나 폐점한 매장은 404이고 경로를 조회하지 않는다")
    void 매장_없음() throws Exception {
        mvc.perform(route(157, "CAR")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STORE404-0"));
        mvc.perform(route(999999, "CAR")).andExpect(status().isNotFound());

        assertThat(CAR.calls).isZero();
    }

    @Test
    @DisplayName("모르는 이동 수단·좌표 누락·대한민국 밖 좌표·잘못된 매장 ID는 400이고 경로를 조회하지 않는다")
    void 잘못된_요청() throws Exception {
        mvc.perform(route(101, "ROCKET")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/stores/101/route").param("originLatitude", "37.5")
                        .param("originLongitude", "127.0"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/stores/101/route").param("mode", "CAR")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/stores/101/route").param("mode", "CAR").param("originLatitude", "35.68")
                        .param("originLongitude", "139.69"))
                .andExpect(status().isBadRequest());
        mvc.perform(route(0, "CAR")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/stores/abc/route").param("mode", "CAR").param("originLatitude", "37.5")
                        .param("originLongitude", "127.0"))
                .andExpect(status().isBadRequest());

        assertThat(CAR.calls).isZero();
    }

    @Test
    @DisplayName("경로가 없으면 404, 길찾기 서비스에 연결하지 못하면 503이다")
    void 경로_실패() throws Exception {
        WALK.respondWith(() -> {
            throw new GeneralException(StoreErrorCode.ROUTE_NOT_FOUND);
        });
        BICYCLE.respondWith(() -> {
            throw new GeneralException(StoreErrorCode.ROUTE_UNAVAILABLE);
        });

        mvc.perform(route(101, "WALK")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STORE404-1"));
        mvc.perform(route(101, "BICYCLE")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("STORE503-2"));
    }

    @Test
    @DisplayName("설정한 최대 경로 거리를 초과하면 400과 거리 초과 코드를 반환한다")
    void 거리_상한_초과() throws Exception {
        CAR.respondWith(() -> StoreRouteResponse.builder().mode(RouteMode.CAR)
                .distanceMeters(20_001).durationSeconds(600).steps(List.of()).build());

        mvc.perform(get("/api/v1/stores/101/route").param("mode", "CAR")
                        .param("originLatitude", "37.555").param("originLongitude", "126.988"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("STORE400-5"));
        assertThat(CAR.calls).isEqualTo(1);
    }

    private MockHttpServletRequestBuilder route(long storeId, String mode) {
        return get("/api/v1/stores/{storeId}/route", storeId)
                .param("mode", mode)
                .param("originLatitude", "37.5663")
                .param("originLongitude", "126.9779");
    }

    @TestConfiguration
    static class StubRouteConfig {

        @Bean
        @Primary
        StoreRouteService stubStoreRouteService(
                StoreQueryService storeQueryService, Clock clock, StoreRouteCacheProperties properties,
                StoreRouteProperties routeProperties) {
            return new StoreRouteService(storeQueryService, new StoreRouteCache(clock, properties),
                    List.of(CAR, new StubClient(RouteMode.TRANSIT), WALK, BICYCLE), routeProperties);
        }
    }

    private static final class StubClient implements StoreRouteClient {

        private final RouteMode mode;
        private Supplier<StoreRouteResponse> response;
        private int calls;

        StubClient(RouteMode mode) {
            this.mode = mode;
            reset();
        }

        void reset() {
            calls = 0;
            response = () -> StoreRouteResponse.arrived(mode);
        }

        void respondWith(Supplier<StoreRouteResponse> response) {
            this.response = response;
        }

        @Override
        public RouteMode mode() {
            return mode;
        }

        @Override
        public StoreRouteResponse findRoute(RouteEndpoints endpoints) {
            calls++;
            return response.get();
        }
    }
}
