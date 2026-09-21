package com.telme.llm.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "llm")
public record LlmProperties(
        String model,
        Duration connectTimeout,
        Duration readTimeout,
        // 모델 한 번에 읽는 토큰 수. 안 보내면 Ollama 기본값 4096으로 동작한다
        @DefaultValue("8192") int contextSize
) {

    public LlmProperties {
        if (contextSize < 1) {
            throw new IllegalArgumentException("컨텍스트 창 크기는 1 이상이어야 합니다.");
        }
    }
}
