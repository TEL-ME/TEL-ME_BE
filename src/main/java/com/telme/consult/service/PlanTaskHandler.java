package com.telme.consult.service;

// 요금제 업무 하나를 맡는다. 업무마다 미리 알려줄 내용과 이동할 화면이 달라 따로 구현한다
public interface PlanTaskHandler {

    PlanTask task();

    // 회원이 아니면 userId는 null. 요금제 이름 같은 정보는 사용자가 쓴 원문을 먼저 보고 없으면 문맥으로 풀어쓴 문장에서 찾는다
    PlanTaskOutcome answer(Long userId, String original, String resolved);

    // recommended는 같은 채팅방의 가장 최근 추천 문장(없으면 null). 원문과 풀어쓴 문장에 요금제 이름이 없을 때 대상으로 쓴다
    default PlanTaskOutcome answer(Long userId, String original, String resolved, String recommended) {
        return answer(userId, original, resolved);
    }
}
