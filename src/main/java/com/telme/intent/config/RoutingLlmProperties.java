package com.telme.intent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// 라우팅 LLM 호출(의도 분류, 요청 개수 판정, 검색어 검증, 후속 분석). bedrock을 켜면 gpt-oss(Bedrock)를 먼저 쓰고
// 실패하면 엑사원으로 대체한다. requestInventoryEnabled는 QueryRoutingService가 쓰는 값과 같고, 여기서는 bedrock과 어긋났는지만 본다
@ConfigurationProperties(prefix = "telme.routing")
public record RoutingLlmProperties(
        @DefaultValue("false") boolean bedrock,
        @DefaultValue("true") boolean requestInventoryEnabled) {
}
