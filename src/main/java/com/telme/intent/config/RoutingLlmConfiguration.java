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
        boolean usesBedrock = usesBedrock(properties, bedrock);
        boolean inventory = properties.inventoryEnabled(usesBedrock);
        // 설정과 라우팅 모델이 어긋나면 값을 바꾸지 않고 경고만 남긴다
        if (usesBedrock && inventory) {
            log.warn("라우팅을 gpt-oss(Bedrock)로 부르는데 요청 개수 판정(ROUTING_REQUEST_INVENTORY_ENABLED)이 켜져 있다. "
                    + "맞게 나뉜 질문을 다시 합칠 수 있다(docs/01-pipeline/02-context-routing/routing-call-comparison.md 2단계)");
        }
        if (!usesBedrock && !inventory) {
            log.warn("라우팅을 엑사원으로 부르는데 요청 개수 판정(ROUTING_REQUEST_INVENTORY_ENABLED)이 꺼져 있다. "
                    + "질문을 잘못 나누는 경우가 늘 수 있다(docs/01-pipeline/02-context-routing/routing-call-comparison.md C절)");
        }
        return usesBedrock ? new FallbackLlmClient(bedrock, exaone) : exaone;
    }

    private static boolean usesBedrock(RoutingLlmProperties properties, LlmClient bedrock) {
        return properties.bedrock() && bedrock != null;
    }

    @Bean(name = ROUTING_LLM_CLIENT, defaultCandidate = false)
    LlmClient routingLlmClient(LlmClient llmClient, RoutingLlmProperties properties,
            @Qualifier(BedrockConfig.JUDGE_LLM_CLIENT) ObjectProvider<LlmClient> bedrock) {
        return routingClient(properties, llmClient, bedrock.getIfAvailable());
    }

    @Bean
    RoutingRequestInventory routingRequestInventory(RoutingLlmProperties properties,
            @Qualifier(BedrockConfig.JUDGE_LLM_CLIENT) ObjectProvider<LlmClient> bedrock) {
        return new RoutingRequestInventory(properties.inventoryEnabled(usesBedrock(properties, bedrock.getIfAvailable())));
    }
}
