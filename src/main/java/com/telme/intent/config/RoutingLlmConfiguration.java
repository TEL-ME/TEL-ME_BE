package com.telme.intent.config;

import com.telme.llm.config.BedrockConfig;
import com.telme.llm.service.FallbackLlmClient;
import com.telme.llm.service.LlmClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 라우팅 서비스가 쓸 모델을 고른다. 이름(ROUTING_LLM_CLIENT)으로만 주입받게 해 다른 호출의 기본 모델은 바꾸지 않는다. */
@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RoutingLlmProperties.class)
public class RoutingLlmConfiguration {

    public static final String ROUTING_LLM_CLIENT = "routingLlmClient";

    static LlmClient routingClient(RoutingLlmProperties properties, LlmClient exaone, LlmClient bedrock) {
        if (!properties.bedrock() || bedrock == null) {
            return exaone;
        }
        // 의도 분류가 gpt-oss면 요청 개수 판정이 맞게 나뉜 질문을 다시 합친다. 자동으로 끄면 설정과 동작이 달라져 경고만 남긴다
        if (properties.requestInventoryEnabled()) {
            log.warn("라우팅을 gpt-oss(Bedrock)로 부르는데 요청 개수 판정(ROUTING_REQUEST_INVENTORY_ENABLED)이 켜져 있다. "
                    + "맞게 나뉜 질문을 다시 합치므로 false로 끈다(docs/ROUTING_CALL_COMPARISON.md 2단계)");
        }
        return new FallbackLlmClient(bedrock, exaone);
    }

    @Bean(name = ROUTING_LLM_CLIENT, defaultCandidate = false)
    LlmClient routingLlmClient(LlmClient llmClient, RoutingLlmProperties properties,
            @Qualifier(BedrockConfig.JUDGE_LLM_CLIENT) ObjectProvider<LlmClient> bedrock) {
        return routingClient(properties, llmClient, bedrock.getIfAvailable());
    }
}
