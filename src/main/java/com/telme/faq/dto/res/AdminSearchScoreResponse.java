package com.telme.faq.dto.res;

import java.util.List;

//상담 경로의 질문별 첫 검색(원문, 또는 이전 대화로 지시어를 풀어 쓴 질문) 기준이다.
//threshold: 현재 Q_A 임계값(그래프의 기준선), total: 질문 수,
//scored: Q_A 1위 점수가 있는 질문 수(= buckets 합. 후보가 없거나 질문 벡터만 조회한 검색은 total에만 들어간다),
//passed: 첫 검색으로 근거를 찾은 질문 수(이중 벡터면 질문 벡터로 통과한 것 포함),
//refinedPassed: 첫 검색은 비었지만 정제 질문으로 근거를 찾은 질문 수,
//aboveThreshold: Q_A 1위 점수가 검색 당시 임계값 이상인 질문 수. buckets는 Q_A 1위 점수 분포(0~1, 0.05 간격)
//threshold·passed·refinedPassed·aboveThreshold는 회색 구간 판정 전 기준(비교용)이고, 판정 뒤 실제 결과는 grayZone에 있다
public record AdminSearchScoreResponse(
        double threshold,
        long total,
        long scored,
        long passed,
        long refinedPassed,
        long aboveThreshold,
        List<Bucket> buckets,
        GrayZone grayZone
        ) {
    // min 이상 max 미만. 마지막 칸만 1.0을 포함한다
    public record Bucket(double min, double max, long count) {
    }

    // 선(qaPass 등)은 현재 설정값이다. 건수는 판정 결과를 남긴 질문만 센다(recorded = 아래 여섯 건수의 합, 서로 겹치지 않는다).
    // passedDirectly: 근거에 판정 없이 통과한 FAQ가 있음(판정한 후보가 함께 있었어도 여기),
    // passedByJudge: 판정기가 답할 수 있다고 본 FAQ로 근거를 찾음, passedOnJudgeFailure: 판정 실패 뒤 점수로 남긴 후보만 근거,
    // rejectedByJudge: 판정기가 후보를 모두 거절해 근거 없음, droppedOnJudgeFailure: 판정 실패로 버린 후보가 있고 근거 없음,
    // belowFloor: 후보가 모두 아래 선 미만이라 근거 없음.
    // refinedFinalPassed: 첫 검색은 비었지만 정제 질문 검색에서 판정까지 거쳐 근거를 찾은 질문 수
    // (바깥 refinedPassed는 판정 전 기준이라 이름을 나눈다)
    public record GrayZone(
            boolean enabled,
            double qaPass,
            double qaFloor,
            double questionPass,
            double questionFloor,
            long recorded,
            long passedDirectly,
            long passedByJudge,
            long passedOnJudgeFailure,
            long rejectedByJudge,
            long droppedOnJudgeFailure,
            long belowFloor,
            long refinedFinalPassed
            ) {
    }
}
