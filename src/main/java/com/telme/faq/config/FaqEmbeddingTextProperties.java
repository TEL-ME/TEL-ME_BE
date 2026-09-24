package com.telme.faq.config;

import com.telme.faq.service.FaqEmbeddingTextVariant;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// 임베딩 텍스트 구성안 선택
// 바꾸면 전량 재임베딩(faq.reembed)이 필요
@ConfigurationProperties(prefix = "faq.embedding-text")
public record FaqEmbeddingTextProperties(
        @DefaultValue("Q_A") FaqEmbeddingTextVariant variant
) {

    public FaqEmbeddingTextProperties {
        if (variant == null) {
            throw new IllegalArgumentException("faq.embedding-text.variant가 필요합니다.");
        }
    }
}
