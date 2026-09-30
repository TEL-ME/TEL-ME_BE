package com.telme.faq.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// 벡터 후보를 리랭커로 다시 채점해 관련성을 판정한다. 켜면 search.similarity-threshold 대신 vectorFloor·threshold가 쓰인다
@ConfigurationProperties(prefix = "search.rerank")
public record SearchRerankProperties(
        @DefaultValue("false") boolean enabled,
        String modelPath,
        String tokenizerPath,
        @DefaultValue("20") int candidateK,
        @DefaultValue("0.0") double vectorFloor,
        @DefaultValue("0.5") double threshold,
        @DefaultValue("512") int maxLength,
        @DefaultValue("8") int batchSize
) {
}
