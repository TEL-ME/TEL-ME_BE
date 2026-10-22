package com.telme.rag.config;

import com.telme.llm.config.BedrockConfig;
import com.telme.llm.service.AnswerRetryGate;
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
        return properties.bedrock() && bedrock != null ? new FallbackLlmClient(bedrock, exaone, new AnswerRetryGate()) : exaone;
    }

    // 금액으로 막힌 답의 다시 생성은 gpt-oss만 부른다. 엑사원 대체는 Bedrock 장애용이고 엑사원 GPU는 평소 꺼져 있어,
    // 다시 생성이 실패해도 엑사원으로 넘기지 않고 안전 안내로 끝낸다. null이면 다시 생성하지 않는다
    public static LlmClient amountRetryClient(RagAnswerProperties properties, LlmClient bedrock) {
        return properties.bedrock() && properties.amountRetry() ? bedrock : null;
    }

    @Bean(name = RAG_ANSWER_LLM_CLIENT, defaultCandidate = false)
    LlmClient ragAnswerLlmClient(LlmClient llmClient, RagAnswerProperties properties,
            @Qualifier(BedrockConfig.JUDGE_LLM_CLIENT) ObjectProvider<LlmClient> bedrock) {
        return answerClient(properties, llmClient, bedrock.getIfAvailable());
    }
}
