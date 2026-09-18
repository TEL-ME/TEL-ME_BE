package com.telme.consult.config;

import com.telme.consult.service.ClarificationTextGenerator;
import com.telme.consult.service.LlmClarificationTextGenerator;
import com.telme.llm.service.FakeLlmClient;
import com.telme.llm.service.LlmClient;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ClarificationConfiguration {
    @Bean
    @ConditionalOnMissingBean(ClarificationTextGenerator.class)
    @ConditionalOnProperty(name = "telme.consult.llm-enabled", havingValue = "true")
    ClarificationTextGenerator llmClarificationGenerator(LlmClient llmClient) {
        // Fake 응답은 사용자에게 질문으로 보여주지 않는다.
        if (llmClient instanceof FakeLlmClient) {
            return ClarificationTextGenerator.template();
        }
        return new LlmClarificationTextGenerator(llmClient);
    }

    @Bean
    @ConditionalOnMissingBean(ClarificationTextGenerator.class)
    @ConditionalOnProperty(
            name = "telme.consult.llm-enabled",
            havingValue = "false",
            matchIfMissing = true)
    ClarificationTextGenerator templateClarificationGenerator() {
        return ClarificationTextGenerator.template();
    }
}
