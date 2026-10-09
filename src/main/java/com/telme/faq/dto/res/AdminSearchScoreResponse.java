package com.telme.faq.dto.res;

import java.util.List;

//상담 경로의 질문별 첫 검색(원문, 또는 이전 대화로 지시어를 풀어 쓴 질문) 기준이다.
//threshold: 현재 Q_A 임계값(그래프의 기준선), total: 질문 수,
//scored: Q_A 1위 점수가 있는 질문 수(= buckets 합. 후보가 없거나 질문 벡터만 조회한 검색은 total에만 들어간다),
//passed: 첫 검색으로 근거를 찾은 질문 수(이중 벡터면 질문 벡터로 통과한 것 포함),
//refinedPassed: 첫 검색은 비었지만 정제 질문으로 근거를 찾은 질문 수,
//aboveThreshold: Q_A 1위 점수가 검색 당시 임계값 이상인 질문 수. buckets는 Q_A 1위 점수 분포(0~1, 0.05 간격)
public record AdminSearchScoreResponse(
        double threshold,
        long total,
        long scored,
        long passed,
        long refinedPassed,
        long aboveThreshold,
        List<Bucket> buckets
        ) {
    // min 이상 max 미만. 마지막 칸만 1.0을 포함한다
    public record Bucket(double min, double max, long count) {
    }
}
