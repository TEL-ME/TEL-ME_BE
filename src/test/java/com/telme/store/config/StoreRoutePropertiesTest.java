package com.telme.store.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.ClassPathResource;

class StoreRoutePropertiesTest {

    @Test
    @DisplayName("설정하지 않으면 최대 거리는 0으로 제한이 없다")
    void 기본값() {
        new ApplicationContextRunner().withUserConfiguration(RouteConfig.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(StoreRouteProperties.class).maxDistanceMeters()).isZero();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "0", "20000", "2147483647"})
    @DisplayName("application.yml의 환경변수로 거리 상한을 바인딩하고 빈 값도 제한이 없다")
    void 환경변수_바인딩(String value) {
        new ApplicationContextRunner().withUserConfiguration(RouteConfig.class)
                .withInitializer(context -> {
                    try {
                        var sources = context.getEnvironment().getPropertySources();
                        for (var source : new YamlPropertySourceLoader()
                                .load("application", new ClassPathResource("application.yml"))) {
                            sources.addLast(source);
                        }
                        sources.addFirst(new SystemEnvironmentPropertySource("route-test-env", Map.of(
                                "STORE_ROUTE_MAX_DISTANCE_METERS", value)));
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                }).run(context -> {
                    assertThat(context).hasNotFailed();
                    int expected = value.isEmpty() ? 0 : Integer.parseInt(value);
                    assertThat(context.getBean(StoreRouteProperties.class).maxDistanceMeters()).isEqualTo(expected);
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "invalid", "2147483648"})
    @DisplayName("음수·잘못된 형식·정수 범위 초과 설정이면 기동에 실패한다")
    void 잘못된_설정(String value) {
        new ApplicationContextRunner().withUserConfiguration(RouteConfig.class)
                .withPropertyValues("store.route.max-distance-meters=" + value)
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(StoreRouteProperties.class)
    static class RouteConfig {
    }
}
