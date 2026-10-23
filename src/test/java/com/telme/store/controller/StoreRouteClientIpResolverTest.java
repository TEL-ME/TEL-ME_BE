package com.telme.store.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.telme.store.config.StoreOriginWalkProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.ForwardedHeaderFilter;

class StoreRouteClientIpResolverTest {

    @Test
    void 기본은_임의_헤더를_무시하고_실제_접속_IP를_쓴다() {
        assertThat(resolve(List.of(), "192.0.2.1", "198.51.100.1")).isEqualTo("192.0.2.1");
        assertThat(resolve(List.of("10.0.0.0/8"), "192.0.2.1", "198.51.100.1")).isEqualTo("192.0.2.1");
    }

    @Test
    void 신뢰하는_프록시만_오른쪽부터_건너뛰고_위조한_앞주소는_무시한다() {
        assertThat(resolve(List.of("10.0.0.0/8"), "10.0.0.2", "203.0.113.99, 192.0.2.1, 10.0.0.1"))
                .isEqualTo("192.0.2.1");
    }

    @Test
    void IPv6도_정규화하고_동일_접속자로_묶는다() {
        assertThat(resolve(List.of("::1/128"), "::1", "2001:db8::1"))
                .isEqualTo(resolve(List.of("::1/128"), "::1", "2001:db8:0:0:0:0:0:1"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"localhost", "dead", "127.1", "999.2.3.4", "", "192.0.2.1, ", "bad::ip"})
    void 잘못된_주소나_호스트이름이면_실제_접속_IP로_묶는다(String forwarded) {
        assertThat(resolve(List.of("10.0.0.0/8"), "10.0.0.2", forwarded)).isEqualTo("10.0.0.2");
    }

    @Test
    void 비정상적으로_긴_헤더는_무시한다() {
        assertThat(resolve(List.of("10.0.0.0/8"), "10.0.0.2", "1".repeat(4097))).isEqualTo("10.0.0.2");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 필터가_IP를_바꾸고_헤더를_감춰도_여러_겹의_래퍼를_벗겨_원본을_읽는다(
            boolean withForwardedHeader) throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/stores/101/route/origin-walk");
        request.setRemoteAddr("10.0.0.2");
        request.addHeader("X-Forwarded-For", "198.51.100.99, 203.0.113.7, 10.0.0.1");
        if (withForwardedHeader) {
            request.addHeader("Forwarded", "for=198.51.100.99;proto=https");
        }
        var resolver = new StoreRouteClientIpResolver(
                new StoreOriginWalkProperties(3000, 30, List.of("10.0.0.0/8")));
        AtomicReference<String> resolved = new AtomicReference<>();
        new ForwardedHeaderFilter().doFilter(request, new MockHttpServletResponse(), (filtered, response) -> {
            var wrapped = (HttpServletRequest) filtered;
            assertThat(wrapped.getRemoteAddr()).isEqualTo("198.51.100.99");
            assertThat(wrapped.getHeader("X-Forwarded-For")).isNull();
            assertThat(wrapped.getHeader("Forwarded")).isNull();
            resolved.set(resolver.resolve(new HttpServletRequestWrapper(new HttpServletRequestWrapper(wrapped))));
        });
        assertThat(resolved.get()).isEqualTo("203.0.113.7");
    }

    private String resolve(List<String> trusted, String peer, String forwarded) {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr(peer);
        request.addHeader("X-Forwarded-For", forwarded);
        return new StoreRouteClientIpResolver(new StoreOriginWalkProperties(3000, 30, trusted)).resolve(request);
    }
}
