package com.telme.llm.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// Bedrock 순간 장애(시간 초과·한도 초과·일시 서버 오류) 자동 재시도. maxAttempts는 첫 호출을 포함한 횟수다
@ConfigurationProperties(prefix = "llm.bedrock.retry")
public record BedrockRetryProperties(
        @DefaultValue("2") int maxAttempts,
        @DefaultValue("2s") Duration waitDuration
) {

    public LlmRetryProperties toRetryProperties() {
        return new LlmRetryProperties(maxAttempts, waitDuration);
    }
}
