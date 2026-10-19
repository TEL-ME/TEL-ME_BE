package com.telme.chat.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.llm.config.BedrockConfig;
import com.telme.llm.service.FallbackLlmClient;
import com.telme.llm.service.LlmClient;
import com.telme.llm.service.SchemaInstructionLlmClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 종료 요약이 쓸 모델을 고른다. 이름(END_SUMMARY_LLM_CLIENT)으로만 주입받게 해 다른 호출의 기본 모델은 바꾸지 않는다. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ChatEndSummaryProperties.class)
public class ChatEndSummaryConfiguration {

    public static final String END_SUMMARY_LLM_CLIENT = "endSummaryLlmClient";

    // Bedrock에는 출력 스키마가 전달되지 않아 지시문으로 붙이고, 엑사원 대체에는 원래 요청(스키마 강제)을 그대로 보낸다
    static LlmClient summaryClient(ChatEndSummaryProperties properties, LlmClient exaone, LlmClient bedrock,
            ObjectMapper mapper) {
        return properties.bedrock() && bedrock != null
                ? new FallbackLlmClient(new SchemaInstructionLlmClient(bedrock, mapper), exaone) : exaone;
    }

    @Bean(name = END_SUMMARY_LLM_CLIENT, defaultCandidate = false)
    LlmClient endSummaryLlmClient(LlmClient llmClient, ChatEndSummaryProperties properties, ObjectMapper mapper,
            @Qualifier(BedrockConfig.JUDGE_LLM_CLIENT) ObjectProvider<LlmClient> bedrock) {
        return summaryClient(properties, llmClient, bedrock.getIfAvailable(), mapper);
    }
}
