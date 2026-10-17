package com.telme.intent.config;

import com.telme.llm.config.BedrockConfig;
import com.telme.llm.service.FallbackLlmClient;
import com.telme.llm.service.LlmClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 라우팅 서비스가 쓸 모델을 고른다. 이름(ROUTING_LLM_CLIENT)으로만 주입받게 해 다른 호출의 기본 모델은 바꾸지 않는다. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RoutingLlmProperties.class)
public class RoutingLlmConfiguration {

    public static final String ROUTING_LLM_CLIENT = "routingLlmClient";

    static LlmClient routingClient(RoutingLlmProperties properties, LlmClient exaone, LlmClient bedrock) {
        return properties.bedrock() && bedrock != null ? new FallbackLlmClient(bedrock, exaone) : exaone;
    }

    @Bean(name = ROUTING_LLM_CLIENT, defaultCandidate = false)
    LlmClient routingLlmClient(LlmClient llmClient, RoutingLlmProperties properties,
            @Qualifier(BedrockConfig.JUDGE_LLM_CLIENT) ObjectProvider<LlmClient> bedrock) {
        return routingClient(properties, llmClient, bedrock.getIfAvailable());
    }
}
