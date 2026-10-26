package com.telme.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// 호출이 하나 늘어 응답이 느려지므로 끌 수 있게 둔다
@ConfigurationProperties(prefix = "rag.evidence-check")
public record EvidenceCheckProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("300") int maxTokens
) {
}
