package com.telme.faq.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mock.env.MockEnvironment;

class SearchPropertiesTest {

    // 기본 러너는 OS 환경변수를 읽어 SEARCH_DUAL_VECTOR_ENABLED 같은 값이 export돼 있으면 기본값 검사가 깨진다
    // MockEnvironment는 OS 환경변수·시스템 프로퍼티를 읽지 않아 withPropertyValues로 준 값만 본다
    private final ApplicationContextRunner runner = new ApplicationContextRunner(() -> {
                AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
                context.setEnvironment(new MockEnvironment());
                return context;
            })
            .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(Config.class);

    @EnableConfigurationProperties(SearchProperties.class)
    static class Config {
    }

    @Test
    @DisplayName("이중 벡터 설정이 없으면 꺼진 상태, 질문만 벡터 임계값 0.88로 바인딩된다")
    void 이중_벡터_기본값() {
        runner.withPropertyValues("search.similarity-threshold=0.72")
                .run(context -> {
                    SearchProperties properties = context.getBean(SearchProperties.class);
                    assertThat(properties.similarityThreshold()).isEqualTo(0.72);
                    assertThat(properties.dualVector().enabled()).isFalse();
                    assertThat(properties.dualVector().questionThreshold()).isEqualTo(0.88);
                });
    }

    @Test
    @DisplayName("설정한 값으로 바인딩된다")
    void 설정값이_바인딩된다() {
        runner.withPropertyValues(
                        "search.similarity-threshold=0.7",
                        "search.dual-vector.enabled=true",
                        "search.dual-vector.question-threshold=0.9")
                .run(context -> {
                    SearchProperties properties = context.getBean(SearchProperties.class);
                    assertThat(properties.dualVector().enabled()).isTrue();
                    assertThat(properties.dualVector().questionThreshold()).isEqualTo(0.9);
                });
    }

    @Test
    @DisplayName("측정용으로 임계값을 0으로 풀 수 있다")
    void 임계값_0을_허용한다() {
        runner.withPropertyValues("search.similarity-threshold=0", "search.dual-vector.question-threshold=0")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    @DisplayName("similarity-threshold가 없으면 기동이 실패한다 - 0으로 조용히 채워지지 않게")
    void 임계값이_없으면_실패한다() {
        runner.run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("임계값이 0~1 밖이면 기동이 실패한다")
    void 범위_밖이면_실패한다() {
        runner.withPropertyValues("search.similarity-threshold=1.2")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("search.similarity-threshold=0.72", "search.dual-vector.question-threshold=-0.1")
                .run(context -> assertThat(context).hasFailed());
    }
}
