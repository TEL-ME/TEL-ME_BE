package com.telme.consult.service;

import java.util.List;

// 안내 문구와 아래 버튼. 버튼 중 PlanTaskLinks의 문구는 프론트가 처리 화면으로 가는 링크로 그린다
public record PlanTaskOutcome(String content, List<String> followUps) {

    static PlanTaskOutcome reply(String content, String... followUps) {
        return new PlanTaskOutcome(content, List.of(followUps));
    }
}
