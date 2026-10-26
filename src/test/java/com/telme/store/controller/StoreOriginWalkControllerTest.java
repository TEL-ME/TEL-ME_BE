package com.telme.store.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.telme.global.common.exception.GeneralException;
import com.telme.global.common.exception.GlobalExceptionHandler;
import com.telme.store.config.StoreOriginWalkProperties;
import com.telme.store.dto.req.RouteMode;
import com.telme.store.dto.req.StoreOriginWalkRequest;
import com.telme.store.dto.res.StoreRouteResponse;
import com.telme.store.exception.StoreErrorCode;
import com.telme.store.service.StoreQueryService;
import com.telme.store.service.StoreRouteService;
import com.telme.store.service.StoreSearchService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class StoreOriginWalkControllerTest {
    private final StoreRouteService routes = mock(StoreRouteService.class);
    private final LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new StoreController(
                        mock(StoreSearchService.class), mock(StoreQueryService.class), routes,
                        new StoreRouteClientIpResolver(new StoreOriginWalkProperties(3000, 30, List.of()))))
                .setValidator(validator).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @AfterEach
    void closeValidator() {
        validator.close();
    }

    @Test
    void 좌표를_바인딩하고_공통_응답으로_반환한다() throws Exception {
        when(routes.getOriginWalk(eq(101L), any(), eq("127.0.0.1"))).thenReturn(StoreRouteResponse.arrived(RouteMode.WALK));
        mvc.perform(get("/api/v1/stores/101/route/origin-walk")
                        .param("originLatitude", "37.5444").param("originLongitude", "127.0374")
                        .param("destinationLatitude", "37.5447").param("destinationLongitude", "127.0366"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.mode").value("WALK"));
        verify(routes).getOriginWalk(101L, new StoreOriginWalkRequest(37.5444, 127.0374, 37.5447, 127.0366), "127.0.0.1");
    }

    @Test
    void 빈도_초과는_429와_전용_오류코드를_반환한다() throws Exception {
        when(routes.getOriginWalk(eq(101L), any(), eq("127.0.0.1")))
                .thenThrow(new GeneralException(StoreErrorCode.ORIGIN_WALK_RATE_LIMIT_EXCEEDED));
        mvc.perform(get("/api/v1/stores/101/route/origin-walk")
                        .param("originLatitude", "37.5444").param("originLongitude", "127.0374")
                        .param("destinationLatitude", "37.5447").param("destinationLongitude", "127.0366"))
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("STORE429-0"));
    }

    @Test
    void 좌표가_누락되면_서비스를_호출하지_않는다() throws Exception {
        mvc.perform(get("/api/v1/stores/101/route/origin-walk")
                        .param("originLatitude", "37.5").param("originLongitude", "127"))
                .andExpect(status().isBadRequest());
        verify(routes, never()).getOriginWalk(eq(101L), any(), eq("127.0.0.1"));
    }

    @Test
    void 목적지가_대한민국_밖이면_서비스를_호출하지_않는다() throws Exception {
        mvc.perform(get("/api/v1/stores/101/route/origin-walk")
                        .param("originLatitude", "37.5").param("originLongitude", "127")
                        .param("destinationLatitude", "37.6").param("destinationLongitude", "139"))
                .andExpect(status().isBadRequest());
        verify(routes, never()).getOriginWalk(eq(101L), any(), eq("127.0.0.1"));
    }
}
