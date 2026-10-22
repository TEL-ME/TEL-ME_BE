package com.telme.store.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.ClassPathResource;

class StoreRouteUsagePropertiesTest {

    @Test
    void 기본_예산과_스위치() {
        new ApplicationContextRunner().withUserConfiguration(Config.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(StoreRouteUsageProperties.class))
                    .isEqualTo(new StoreRouteUsageProperties(true, 9000, 900, 900, 900));
        });
    }

    @Test
    void 환경변수_바인딩() {
        new ApplicationContextRunner().withUserConfiguration(Config.class).withInitializer(context -> {
            try {
                var sources = context.getEnvironment().getPropertySources();
                for (var source : new YamlPropertySourceLoader()
                        .load("application", new ClassPathResource("application.yml"))) {
                    sources.addLast(source);
                }
                sources.addFirst(new SystemEnvironmentPropertySource("route-usage-test", Map.of(
                        "STORE_ROUTE_ENABLED", "false", "STORE_ROUTE_CAR_DAILY_LIMIT", "0",
                        "STORE_ROUTE_TRANSIT_DAILY_LIMIT", "11", "STORE_ROUTE_WALK_DAILY_LIMIT", "12",
                        "STORE_ROUTE_BICYCLE_DAILY_LIMIT", "13")));
            } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
            }
        }).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(StoreRouteUsageProperties.class))
                    .isEqualTo(new StoreRouteUsageProperties(false, 0, 11, 12, 13));
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"car", "transit", "walk", "bicycle"})
    void 음수_예산은_기동_실패(String mode) {
        new ApplicationContextRunner().withUserConfiguration(Config.class)
                .withPropertyValues("store.route." + mode + "-daily-limit=-1")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(StoreRouteUsageProperties.class)
    static class Config {
    }
}
