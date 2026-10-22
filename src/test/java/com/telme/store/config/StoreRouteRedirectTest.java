package com.telme.store.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class StoreRouteRedirectTest {

    @Test
    void 경로_HTTP는_예약하지_않은_리다이렉트_호출을_하지_않음() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger followed = new AtomicInteger();
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", "/target");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/target", exchange -> {
            followed.incrementAndGet();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            var properties = new KakaoRouteProperties(baseUrl, baseUrl, "test-key",
                    Duration.ofSeconds(1), Duration.ofSeconds(1), false);
            var config = new StoreConfig();
            for (RestClient client : new RestClient[] {
                    config.kakaoRouteRestClient(RestClient.builder(), properties),
                    config.kakaoNaviRestClient(RestClient.builder(), properties)}) {
                int status = client.get().uri("/redirect")
                        .exchange((request, response) -> response.getStatusCode().value());
                assertThat(status).isEqualTo(302);
            }
            assertThat(followed.get()).isZero();
        } finally {
            server.stop(0);
        }
    }
}
