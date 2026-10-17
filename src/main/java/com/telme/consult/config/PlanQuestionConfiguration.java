package com.telme.consult.config;

import com.telme.llm.config.BedrockConfig;
import com.telme.llm.service.FallbackLlmClient;
import com.telme.llm.service.LlmClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// 요금제 질문 분류가 쓸 모델을 고른다. 이름(PLAN_QUESTION_LLM_CLIENT)으로만 주입받게 해 다른 호출의 기본 모델은 바꾸지 않는다
@Configuration
@EnableConfigurationProperties(PlanQuestionProperties.class)
public class PlanQuestionConfiguration {

    public static final String PLAN_QUESTION_LLM_CLIENT = "planQuestionLlmClient";

    static LlmClient classifierClient(PlanQuestionProperties properties, LlmClient exaone, LlmClient bedrock) {
        return properties.bedrock() && bedrock != null ? new FallbackLlmClient(bedrock, exaone) : exaone;
    }

    @Bean(name = PLAN_QUESTION_LLM_CLIENT, defaultCandidate = false)
    LlmClient planQuestionLlmClient(LlmClient llmClient, PlanQuestionProperties properties,
            @Qualifier(BedrockConfig.JUDGE_LLM_CLIENT) ObjectProvider<LlmClient> bedrock) {
        return classifierClient(properties, llmClient, bedrock.getIfAvailable());
    }
}
