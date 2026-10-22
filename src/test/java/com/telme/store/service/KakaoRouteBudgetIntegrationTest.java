package com.telme.store.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.telme.global.common.exception.GeneralException;
import com.telme.store.config.KakaoRouteProperties;
import com.telme.store.config.StoreRouteUsageProperties;
import com.telme.store.converter.StoreRouteConverter;
import com.telme.store.dto.req.RouteMode;
import com.telme.store.exception.StoreErrorCode;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

// DB 예약은 실제 프록시를 사용하고 외부 HTTP는 모두 mock이다.
@SpringBootTest
class KakaoRouteBudgetIntegrationTest {

    private static final LocalDate DAY = LocalDate.of(2002, 1, 1);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2002-01-01T00:00:00Z"), ZoneOffset.UTC);
    private static final RouteEndpoints ENDPOINTS = new RouteEndpoints(new BigDecimal("37.566"),
            new BigDecimal("126.978"), new BigDecimal("37.497942"), new BigDecimal("127.027621"));
    private static final String TRANSIT = """
            {"status":"OK","routes":[{"properties":{"totalDistance":1000,"totalTime":600},
            "steps":[{"properties":{"type":"BUS","distance":1000,"time":600,
            "vehicles":[{"name":"76"}]},"path":{"points":[[126.978,37.567]]}}]}]}
            """;
    private static final String WALK = """
            {"status":"OK","route":{"properties":{"totalDistance":120,"totalTime":100},
            "legs":[{"steps":[{"properties":{"distance":120,"time":100},
            "path":{"points":[[126.978,37.566],[126.978,37.567]]}}]}]}}
            """;
    @Autowired private StoreRouteDailyUsageService dailyUsageService;
    @Autowired private JdbcTemplate jdbc;
    private MockRestServiceServer server;
    private Map<RouteMode, StoreRouteClient> clients;
    private KakaoRouteCalls calls;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://kakao.test");
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient rest = builder.build();
        StoreRouteConverter converter = new StoreRouteConverter();
        calls = new KakaoRouteCalls(new StoreRouteUsageGuard(
                new StoreRouteUsageProperties(true, 1, 1, 1, 1), dailyUsageService, CLOCK));
        KakaoWalkRouteClient walk = new KakaoWalkRouteClient(rest, converter, calls);
        clients = Map.of(RouteMode.CAR, new KakaoNaviCarRouteClient(rest, converter, calls),
                RouteMode.TRANSIT, new KakaoTransitRouteClient(rest, converter, walk,
                        new KakaoRouteProperties("http://kakao.test", "http://kakao.test", "test-key",
                                Duration.ofSeconds(2), Duration.ofSeconds(5), true), calls),
                RouteMode.WALK, walk, RouteMode.BICYCLE, new KakaoBicycleRouteClient(rest, converter, calls));
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from store_route_daily_usages where usage_date in (?, ?)", DAY, DAY.plusDays(1));
    }

    @ParameterizedTest
    @EnumSource(RouteMode.class)
    void 모든_클라이언트에서_한도_후에는_HTTP를_호출하지_않음(RouteMode mode) {
        String body = mode == RouteMode.CAR ? "{\"routes\":[{\"result_code\":104}]}"
                : mode == RouteMode.TRANSIT ? "{\"status\":\"EQUAL_POINTS\"}"
                : "{\"status\":\"SAME_POINT\"}";
        expect(path(mode), body);
        clients.get(mode).findRoute(ENDPOINTS);
        assertDailyLimit(() -> clients.get(mode).findRoute(ENDPOINTS));
        assertThat(used(mode)).isEqualTo(1);
        server.verify();
    }

    @Test
    void 보강도_같은_도보_예산을_사용() {
        expect(path(RouteMode.TRANSIT), TRANSIT);
        expect(path(RouteMode.WALK), WALK);
        assertThat(clients.get(RouteMode.TRANSIT).findRoute(ENDPOINTS).distanceMeters()).isEqualTo(1120);
        assertThat(used(RouteMode.TRANSIT)).isEqualTo(1);
        assertThat(used(RouteMode.WALK)).isEqualTo(1);
        assertDailyLimit(() -> clients.get(RouteMode.WALK).findRoute(ENDPOINTS));
        server.verify();
    }

    @Test
    void 도보_예산_소진이면_보강_HTTP없이_대중교통_반환() {
        assertThat(dailyUsageService.tryReserve(DAY, RouteMode.WALK, 1)).isTrue();
        expect(path(RouteMode.TRANSIT), TRANSIT);
        assertThat(clients.get(RouteMode.TRANSIT).findRoute(ENDPOINTS).distanceMeters()).isEqualTo(1000);
        assertThat(used(RouteMode.WALK)).isEqualTo(1);
        server.verify();
    }

    @Test
    void 실패_후에도_예약을_유지하고_HTTP_동안_DB트랜잭션_없음() {
        assertThatThrownBy(() -> calls.call(RouteMode.CAR, "test", () -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            throw new ResourceAccessException("timeout");
        })).isInstanceOf(GeneralException.class);
        assertThat(used(RouteMode.CAR)).isEqualTo(1);
        assertDailyLimit(() -> calls.call(RouteMode.CAR, "test", () -> "must not run"));
    }

    @Test
    void 다음날과_새_보호객체에서_날짜별_예산_유지() {
        calls.call(RouteMode.CAR, "test", () -> "ok");
        KakaoRouteCalls recreated = new KakaoRouteCalls(new StoreRouteUsageGuard(
                new StoreRouteUsageProperties(true, 1, 1, 1, 1), dailyUsageService, CLOCK));
        assertDailyLimit(() -> recreated.call(RouteMode.CAR, "test", () -> "must not run"));
        KakaoRouteCalls tomorrow = new KakaoRouteCalls(new StoreRouteUsageGuard(
                new StoreRouteUsageProperties(true, 1, 1, 1, 1), dailyUsageService,
                Clock.offset(CLOCK, Duration.ofDays(1))));
        assertThat(tomorrow.call(RouteMode.CAR, "test", () -> "ok")).isEqualTo("ok");
    }

    private void expect(String path, String body) {
        server.expect(requestTo(startsWith("http://kakao.test" + path)))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private long used(RouteMode mode) {
        return jdbc.queryForObject(
                "select used_amount from store_route_daily_usages where usage_date = ? and kind = ?",
                Long.class, DAY, mode.name());
    }

    private void assertDailyLimit(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(GeneralException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(StoreErrorCode.ROUTE_DAILY_LIMIT_EXCEEDED));
    }

    private String path(RouteMode mode) {
        return switch (mode) {
            case CAR -> "/v1/directions";
            case TRANSIT -> "/v2/routing/publictraffic";
            case WALK -> "/v2/routing/walk";
            case BICYCLE -> "/v2/routing/bicycle";
        };
    }
}
