package com.telme.faq.dto.res;

import java.util.List;

// total: 검색 수, passed: 실제로 근거를 찾은 검색 수(이중 벡터면 질문 벡터로 통과한 것 포함),
// aboveThreshold: Q_A 1위 점수가 threshold 이상인 검색 수. buckets는 Q_A 1위 점수 분포(0~1, 0.05 간격)
public record AdminSearchScoreResponse(
        double threshold,
        long total,
        long passed,
        long aboveThreshold,
        List<Bucket> buckets
        ) {
    // min 이상 max 미만. 마지막 칸만 1.0을 포함한다
    public record Bucket(double min, double max, long count) {
    }
}
