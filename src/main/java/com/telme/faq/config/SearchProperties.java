package com.telme.faq.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// 검색 임계값 설정
// similarityThreshold는 질문+답변 벡터(Q_A) 기준, dualVector는 질문만 벡터(QUESTION_ONLY)를 함께 검색할 때 쓴다
// 두 벡터는 점수 분포가 달라 임계값을 따로 둔다
@ConfigurationProperties(prefix = "search")
public record SearchProperties(
        Double similarityThreshold,
        @DefaultValue DualVector dualVector
) {

    public SearchProperties {
        // 기존 @Value 주입처럼 값이 없으면 기동에 실패하게 한다. primitive로 받으면 0으로 조용히 채워진다
        if (similarityThreshold == null) {
            throw new IllegalArgumentException("search.similarity-threshold가 설정되지 않았습니다");
        }
        requireScore("search.similarity-threshold", similarityThreshold);
    }

    public record DualVector(
            @DefaultValue("false") boolean enabled,
            @DefaultValue("0.88") double questionThreshold
    ) {

        public DualVector {
            requireScore("search.dual-vector.question-threshold", questionThreshold);
        }
    }

    // 측정 때는 임계값을 0으로 풀어 쓰므로 0은 허용한다
    private static void requireScore(String key, double value) {
        if (!(value >= 0 && value <= 1)) {
            throw new IllegalArgumentException(key + "는 0~1이어야 합니다: " + value);
        }
    }
}
