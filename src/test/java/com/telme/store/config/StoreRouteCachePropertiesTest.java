package com.telme.store.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.ClassPathResource;

class StoreRouteCachePropertiesTest {

    @Test
    @DisplayName("설정하지 않으면 기존 TTL과 좌표 정밀도를 유지한다")
    void 기본값() {
        new ApplicationContextRunner().withUserConfiguration(CacheConfig.class).run(context -> {
            assertThat(context).hasNotFailed();
            StoreRouteCacheProperties properties = context.getBean(StoreRouteCacheProperties.class);
            assertThat(properties.carTtl()).isEqualTo(Duration.ofMinutes(2));
            assertThat(properties.transitTtl()).isEqualTo(Duration.ofMinutes(5));
            assertThat(properties.walkTtl()).isEqualTo(Duration.ofMinutes(10));
            assertThat(properties.bicycleTtl()).isEqualTo(Duration.ofMinutes(10));
            assertThat(properties.carTransitOriginScale()).isEqualTo(3);
            assertThat(properties.walkBicycleOriginScale()).isEqualTo(4);
        });
    }

    @Test
    @DisplayName("application.yml의 여섯 환경변수가 실제 설정 객체에 반영된다")
    void 환경변수_바인딩() {
        new ApplicationContextRunner().withUserConfiguration(CacheConfig.class)
                .withInitializer(context -> {
                    try {
                        var sources = context.getEnvironment().getPropertySources();
                        for (var source : new YamlPropertySourceLoader()
                                .load("application", new ClassPathResource("application.yml"))) {
                            sources.addLast(source);
                        }
                        sources.addFirst(new SystemEnvironmentPropertySource("cache-test-env", Map.of(
                                "STORE_ROUTE_CACHE_CAR_TTL", "7s",
                                "STORE_ROUTE_CACHE_TRANSIT_TTL", "11s",
                                "STORE_ROUTE_CACHE_WALK_TTL", "13s",
                                "STORE_ROUTE_CACHE_BICYCLE_TTL", "17s",
                                "STORE_ROUTE_CACHE_CAR_TRANSIT_ORIGIN_SCALE", "5",
                                "STORE_ROUTE_CACHE_WALK_BICYCLE_ORIGIN_SCALE", "6")));
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                }).run(context -> {
                    assertThat(context).hasNotFailed();
                    StoreRouteCacheProperties properties = context.getBean(StoreRouteCacheProperties.class);
                    assertThat(properties.carTtl()).isEqualTo(Duration.ofSeconds(7));
                    assertThat(properties.transitTtl()).isEqualTo(Duration.ofSeconds(11));
                    assertThat(properties.walkTtl()).isEqualTo(Duration.ofSeconds(13));
                    assertThat(properties.bicycleTtl()).isEqualTo(Duration.ofSeconds(17));
                    assertThat(properties.carTransitOriginScale()).isEqualTo(5);
                    assertThat(properties.walkBicycleOriginScale()).isEqualTo(6);
                });
    }

    @ParameterizedTest(name = "{0}={1}")
    @MethodSource("invalidSettings")
    @DisplayName("허용 범위 밖이나 형식이 잘못된 설정이면 기동에 실패한다")
    void 잘못된_설정(String name, String value) {
        new ApplicationContextRunner().withUserConfiguration(CacheConfig.class)
                .withPropertyValues("store.route-cache." + name + "=" + value)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("빈 TTL 설정은 Spring 기본값 바인딩 규칙대로 기존 기본값을 사용한다")
    void 빈_TTL_기본값() {
        new ApplicationContextRunner().withUserConfiguration(CacheConfig.class)
                .withPropertyValues("store.route-cache.car-ttl=")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(StoreRouteCacheProperties.class).carTtl())
                            .isEqualTo(Duration.ofMinutes(2));
                });
    }

    @Test
    @DisplayName("허용 범위의 최소·최대값은 사용할 수 있다")
    void 경계값() {
        new ApplicationContextRunner().withUserConfiguration(CacheConfig.class)
                .withPropertyValues("store.route-cache.car-ttl=1s", "store.route-cache.transit-ttl=1h",
                        "store.route-cache.car-transit-origin-scale=3",
                        "store.route-cache.walk-bicycle-origin-scale=6")
                .run(context -> assertThat(context).hasNotFailed());
    }

    private static Stream<Arguments> invalidSettings() {
        Stream<Arguments> ttls = Stream.of("car-ttl", "transit-ttl", "walk-ttl", "bicycle-ttl")
                .flatMap(name -> Stream.of("0s", "-1s", "999ms", "61m", "invalid")
                        .map(value -> Arguments.of(name, value)));
        Stream<Arguments> scales = Stream.of("car-transit-origin-scale", "walk-bicycle-origin-scale")
                .flatMap(name -> Stream.of("2", "7", "invalid")
                        .map(value -> Arguments.of(name, value)));
        return Stream.concat(ttls, scales);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(StoreRouteCacheProperties.class)
    static class CacheConfig {
    }
}
