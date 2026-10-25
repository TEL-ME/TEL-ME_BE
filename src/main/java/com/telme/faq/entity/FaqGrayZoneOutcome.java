package com.telme.faq.entity;

// 회색 구간 판정이 끝난 뒤 한 검색의 결과. faq_search_scores.gray_zone_outcome에 이름 그대로 저장하고, 값끼리 겹치지 않는다.
// 판정 실패(시간 초과·오류)는 점수로 대신 정하므로, 판정기가 살리거나 거절한 결과와 나눠 센다. PASSED로 시작하는 값만 근거를 찾은 검색이다
public enum FaqGrayZoneOutcome {

    // 근거에 판정 없이 통과한 FAQ가 있다. 판정한 후보가 함께 있었어도 판정과 관계없이 찾은 검색이다
    PASSED_DIRECTLY,
    // 바로 통과한 FAQ는 없고, 판정기가 답할 수 있다고 본 FAQ가 근거에 있다
    PASSED_BY_JUDGE,
    // 근거가 판정 실패 뒤 점수(판정 전 임계값 이상)로 남긴 후보뿐이다
    PASSED_ON_JUDGE_FAILURE,
    // 근거가 없고, 판정한 후보를 판정기가 모두 거절했다
    REJECTED_BY_JUDGE,
    // 근거가 없고, 판정 실패로 버린 후보가 있다. 판정이 됐다면 근거가 됐을 수 있다
    DROPPED_ON_JUDGE_FAILURE,
    // 판정할 후보도 없이 근거가 없다(후보가 모두 아래 선 미만)
    BELOW_FLOOR
}
