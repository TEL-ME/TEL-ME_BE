package com.telme.faq.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// FAQ 배치 적재 설정
// batchSize = embedBatch 1회 건수 = 트랜잭션 1개 건수
@ConfigurationProperties(prefix = "faq.batch-load")
public record FaqBatchLoadProperties(
        @DefaultValue("false") boolean enabled,
        String path,
        @DefaultValue("50") int batchSize,
        @DefaultValue("true") boolean exitAfter
) {

    public static final int MAX_BATCH_SIZE = 50;

    public FaqBatchLoadProperties {
        if (batchSize < 1 || batchSize > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException(
                    "faq.batch-load.batch-size는 1~" + MAX_BATCH_SIZE + "이어야 합니다: " + batchSize);
        }
        if (enabled && (path == null || path.isBlank())) {
            throw new IllegalArgumentException("faq.batch-load.enabled=true면 faq.batch-load.path가 필요합니다.");
        }
    }
}
