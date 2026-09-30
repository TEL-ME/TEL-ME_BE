package com.telme.faq.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.telme.faq.config.SearchRerankProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class OnnxFaqRerankerConditionTest {

    @Configuration
    @EnableConfigurationProperties(SearchRerankProperties.class)
    static class PropertiesConfig {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfig.class, OnnxFaqReranker.class);

    @Test
    @DisplayName("설정이 없으면 리랭커 빈을 만들지 않는다")
    void 기본값에서는_빈이_없다() {
        runner.run(context -> assertThat(context).doesNotHaveBean(FaqReranker.class));
    }

    @Test
    @DisplayName("enabled=false면 리랭커 빈을 만들지 않는다")
    void 꺼져_있으면_빈이_없다() {
        runner.withPropertyValues("search.rerank.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(FaqReranker.class));
    }

    @Test
    @DisplayName("켰는데 모델 경로가 비어 있으면 기동에 실패한다")
    void 켰는데_경로가_없으면_기동에_실패한다() {
        runner.withPropertyValues("search.rerank.enabled=true")
                .run(context -> assertThat(context).hasFailed());
    }
}
