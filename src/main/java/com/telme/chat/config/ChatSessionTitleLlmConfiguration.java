package com.telme.chat.config;

import com.telme.llm.config.BedrockConfig;
import com.telme.llm.service.FallbackLlmClient;
import com.telme.llm.service.LlmClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 세션 제목 생성이 쓸 모델을 고른다. 이름(SESSION_TITLE_LLM_CLIENT)으로만 주입받게 해 다른 호출의 기본 모델은 바꾸지 않는다. */
@Configuration(proxyBeanMethods = false)
public class ChatSessionTitleLlmConfiguration {

    public static final String SESSION_TITLE_LLM_CLIENT = "sessionTitleLlmClient";

    static LlmClient titleClient(ChatSessionTitleProperties properties, LlmClient exaone, LlmClient bedrock) {
        return properties.bedrock() && bedrock != null ? new FallbackLlmClient(bedrock, exaone) : exaone;
    }

    @Bean(name = SESSION_TITLE_LLM_CLIENT, defaultCandidate = false)
    LlmClient sessionTitleLlmClient(LlmClient llmClient, ChatSessionTitleProperties properties,
            @Qualifier(BedrockConfig.JUDGE_LLM_CLIENT) ObjectProvider<LlmClient> bedrock) {
        return titleClient(properties, llmClient, bedrock.getIfAvailable());
    }
}
