package com.telme.consult.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.consult.service.LlmComparisonEvidenceResolver;
import com.telme.consult.service.LlmFaqCandidateEvidenceResolver;
import com.telme.llm.config.BedrockConfig;
import com.telme.llm.service.FallbackLlmClient;
import com.telme.llm.service.LlmClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 두 근거 검증 호출이 쓸 모델을 고른다. 상담 연동 설정과 별개로 항상 만든다. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(EvidenceResolverProperties.class)
public class EvidenceResolverConfiguration {

    static LlmClient evidenceClient(EvidenceResolverProperties properties, LlmClient exaone, LlmClient bedrock) {
        return properties.bedrock() && bedrock != null ? new FallbackLlmClient(bedrock, exaone) : exaone;
    }

    @Bean
    LlmComparisonEvidenceResolver comparisonEvidenceResolver(
            LlmClient llmClient, ObjectMapper objectMapper, EvidenceResolverProperties properties,
            @Qualifier(BedrockConfig.JUDGE_LLM_CLIENT) ObjectProvider<LlmClient> bedrock) {
        return new LlmComparisonEvidenceResolver(
                evidenceClient(properties, llmClient, bedrock.getIfAvailable()), objectMapper);
    }

    @Bean
    LlmFaqCandidateEvidenceResolver faqCandidateEvidenceResolver(
            LlmClient llmClient, ObjectMapper objectMapper, EvidenceResolverProperties properties,
            @Qualifier(BedrockConfig.JUDGE_LLM_CLIENT) ObjectProvider<LlmClient> bedrock) {
        return new LlmFaqCandidateEvidenceResolver(
                evidenceClient(properties, llmClient, bedrock.getIfAvailable()), objectMapper);
    }
}
