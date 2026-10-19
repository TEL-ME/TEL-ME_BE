package com.telme.intent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// 라우팅 LLM 호출(의도 분류, 요청 개수 판정, 검색어 검증, 후속 분석). bedrock을 켜면 gpt-oss(Bedrock)를 먼저 쓰고
// 실패하면 엑사원으로 대체한다. requestInventoryEnabled는 비워 두면 라우팅 모델을 따라간다(gpt-oss면 끄고 엑사원이면 켠다)
@ConfigurationProperties(prefix = "telme.routing")
public record RoutingLlmProperties(
        @DefaultValue("true") boolean bedrock,
        Boolean requestInventoryEnabled) {

    public boolean inventoryEnabled(boolean routingUsesBedrock) {
        return requestInventoryEnabled != null ? requestInventoryEnabled : !routingUsesBedrock;
    }
}
