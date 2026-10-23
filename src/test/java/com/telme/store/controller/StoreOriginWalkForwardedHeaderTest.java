package com.telme.store.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.telme.global.common.exception.GlobalExceptionHandler;
import com.telme.store.config.StoreOriginWalkProperties;
import com.telme.store.config.StoreRouteProperties;
import com.telme.store.dto.req.RouteMode;
import com.telme.store.dto.res.StoreRouteResponse;
import com.telme.store.entity.Store;
import com.telme.store.service.StoreOriginWalkGuard;
import com.telme.store.service.StoreQueryService;
import com.telme.store.service.StoreRouteClient;
import com.telme.store.service.StoreRouteService;
import com.telme.store.service.StoreRouteUsageGuard;
import com.telme.store.service.StoreSearchService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.filter.ForwardedHeaderFilter;

// application-prod.yml의 framework 전략과 동일한 필터를 통과시킨다. 외부 경로 클라이언트만 mock이다.
class StoreOriginWalkForwardedHeaderTest {

    private final StoreQueryService stores = mock(StoreQueryService.class);
    private final LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    private final List<StoreRouteClient> clients = Arrays.stream(RouteMode.values()).map(mode -> {
        StoreRouteClient client = mock(StoreRouteClient.class);
        when(client.mode()).thenReturn(mode);
        return client;
    }).toList();
    private final StoreRouteClient walk = clients.stream()
            .filter(client -> client.mode() == RouteMode.WALK).findFirst().orElseThrow();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        validator.afterPropertiesSet();
        var properties = new StoreOriginWalkProperties(3000, 30, List.of("10.0.0.0/8"));
        Clock clock = Clock.fixed(Instant.parse("2026-10-23T00:00:00Z"), ZoneOffset.UTC);
        var routes = new StoreRouteService(stores, clients, new StoreRouteProperties(0),
                mock(StoreRouteUsageGuard.class), new StoreOriginWalkGuard(properties, clock));
        when(stores.getOpenStore(101L)).thenReturn(Store.builder().storeId(101L).build());
        when(walk.findRoute(any())).thenReturn(StoreRouteResponse.arrived(RouteMode.WALK));
        mvc = MockMvcBuilders.standaloneSetup(new StoreController(mock(StoreSearchService.class), stores,
                        routes, new StoreRouteClientIpResolver(properties)))
                .setValidator(validator).setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new ForwardedHeaderFilter()).build();
    }

    @AfterEach
    void closeValidator() {
        validator.close();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 필터가_가짜_IP를_반영해도_실제_접속자의_31번째_요청은_외부_호출_전에_차단한다(
            boolean throughProxy) throws Exception {
        for (int i = 1; i <= 30; i++) {
            mvc.perform(request(throughProxy, "203.0.113.7", i)).andExpect(status().isOk());
        }
        mvc.perform(request(throughProxy, "203.0.113.7", 31))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("STORE429-0"));
        verify(walk, times(30)).findRoute(any());
        verify(stores, times(30)).getOpenStore(101L);

        // 실제 접속자가 다르면 같은 프록시를 거쳐도 차단되지 않는다.
        mvc.perform(request(throughProxy, "203.0.113.8", 32)).andExpect(status().isOk());
        verify(walk, times(31)).findRoute(any());
    }

    private MockHttpServletRequestBuilder request(boolean throughProxy, String clientIp, int fakeIp) {
        String peer = throughProxy ? "10.0.0.2" : clientIp;
        String forwardedFor = "198.51.100." + fakeIp + ", " + clientIp;
        if (throughProxy) {
            forwardedFor += ", 10.0.0.1";
        }
        return get("/api/v1/stores/101/route/origin-walk")
                .with(request -> {
                    request.setRemoteAddr(peer);
                    return request;
                })
                .header("X-Forwarded-For", forwardedFor)
                .header("X-Forwarded-Proto", "https")
                .param("originLatitude", "37.5444").param("originLongitude", "127.0374")
                .param("destinationLatitude", "37.5447").param("destinationLongitude", "127.0366");
    }
}
