package com.telme.faq.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// 전량 재임베딩 설정
// batchSize = embedBatch 1회 건수 = 트랜잭션 1개 건수
// 적재 러너와 마찬가지로 단일 프로세스 전제
@ConfigurationProperties(prefix = "faq.reembed")
public record FaqReembedProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("50") int batchSize,
        @DefaultValue("true") boolean exitAfter
) {

    public static final int MAX_BATCH_SIZE = 50;

    public FaqReembedProperties {
        if (batchSize < 1 || batchSize > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException(
                    "faq.reembed.batch-size는 1~" + MAX_BATCH_SIZE + "이어야 합니다: " + batchSize);
        }
    }
}
