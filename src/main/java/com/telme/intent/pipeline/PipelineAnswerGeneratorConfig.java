package com.telme.intent.pipeline;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PipelineAnswerGeneratorConfig {

    // @ConditionalOnMissingBean은 @Bean 메서드에서만 평가 순서가 보장돼 클래스에 직접 붙이지 않는다
    @Bean
    @ConditionalOnMissingBean(AnswerGenerator.class)
    public AnswerGenerator dummyAnswerGenerator() {
        return new DummyAnswerGenerator();
    }
}
