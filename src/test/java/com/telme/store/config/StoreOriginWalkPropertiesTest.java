package com.telme.store.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
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

class StoreOriginWalkPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Config.class);

    @Test
    void 기본값은_3km와_분당_30회이고_프록시를_신뢰하지_않는다() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            var properties = context.getBean(StoreOriginWalkProperties.class);
            assertThat(properties.maxStraightDistanceMeters()).isEqualTo(3000);
            assertThat(properties.requestsPerMinute()).isEqualTo(30);
            assertThat(properties.trustedProxies()).isEmpty();
        });
    }

    @Test
    void 실제_YAML을_통해_환경변수와_프록시_목록을_바인딩한다() {
        withEnvironment(Map.of("STORE_ROUTE_ORIGIN_WALK_MAX_STRAIGHT_DISTANCE_METERS", "1500",
                "STORE_ROUTE_ORIGIN_WALK_REQUESTS_PER_MINUTE", "60",
                "STORE_ROUTE_ORIGIN_WALK_TRUSTED_PROXIES", "10.0.0.0/8,192.0.2.1"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var properties = context.getBean(StoreOriginWalkProperties.class);
                    assertThat(properties.maxStraightDistanceMeters()).isEqualTo(1500);
                    assertThat(properties.requestsPerMinute()).isEqualTo(60);
                    assertThat(properties.trustedProxies()).containsExactly("10.0.0.0/8", "192.0.2.1");
                });
    }

    @Test
    void 빈_환경변수도_기본_보호값을_유지한다() {
        withEnvironment(Map.of("STORE_ROUTE_ORIGIN_WALK_MAX_STRAIGHT_DISTANCE_METERS", "",
                "STORE_ROUTE_ORIGIN_WALK_REQUESTS_PER_MINUTE", "",
                "STORE_ROUTE_ORIGIN_WALK_TRUSTED_PROXIES", ""))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var properties = context.getBean(StoreOriginWalkProperties.class);
                    assertThat(properties.maxStraightDistanceMeters()).isEqualTo(3000);
                    assertThat(properties.requestsPerMinute()).isEqualTo(30);
                    assertThat(properties.trustedProxies()).isEmpty();
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "invalid", "2147483648"})
    void 보호를_끄거나_잘못된_값을_넣으면_기동하지_않는다(String value) {
        for (String name : new String[] {"max-straight-distance-meters", "requests-per-minute"}) {
            runner.withPropertyValues("store.route.origin-walk." + name + "=" + value)
                    .run(context -> assertThat(context).hasFailed());
        }
    }

    @Test
    void 잘못된_프록시_설정이면_기동하지_않는다() {
        runner.withPropertyValues("store.route.origin-walk.trusted-proxies=not-an-ip")
                .run(context -> assertThat(context).hasFailed());
    }

    private ApplicationContextRunner withEnvironment(Map<String, Object> values) {
        return runner.withInitializer(context -> {
            try {
                var sources = context.getEnvironment().getPropertySources();
                for (var source : new YamlPropertySourceLoader()
                        .load("application", new ClassPathResource("application.yml"))) {
                    sources.addLast(source);
                }
                sources.addFirst(new SystemEnvironmentPropertySource("origin-walk-test-env", values));
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(StoreOriginWalkProperties.class)
    static class Config {
    }
}
