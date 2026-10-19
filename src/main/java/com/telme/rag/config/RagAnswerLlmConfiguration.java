package com.telme.rag.config;

import com.telme.llm.config.BedrockConfig;
import com.telme.llm.service.FallbackLlmClient;
import com.telme.llm.service.LlmClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 답변 생성이 쓸 모델을 고른다. 이름(RAG_ANSWER_LLM_CLIENT)으로만 주입받게 해 다른 호출의 기본 모델은 바꾸지 않는다. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RagAnswerProperties.class)
public class RagAnswerLlmConfiguration {

    public static final String RAG_ANSWER_LLM_CLIENT = "ragAnswerLlmClient";

    static LlmClient answerClient(RagAnswerProperties properties, LlmClient exaone, LlmClient bedrock) {
        return properties.bedrock() && bedrock != null ? new FallbackLlmClient(bedrock, exaone) : exaone;
    }

    @Bean(name = RAG_ANSWER_LLM_CLIENT, defaultCandidate = false)
    LlmClient ragAnswerLlmClient(LlmClient llmClient, RagAnswerProperties properties,
            @Qualifier(BedrockConfig.JUDGE_LLM_CLIENT) ObjectProvider<LlmClient> bedrock) {
        return answerClient(properties, llmClient, bedrock.getIfAvailable());
    }
}
