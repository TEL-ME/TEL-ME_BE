package com.telme.consult.repository;

import java.util.Map;

/** 조건 이름으로 되물은 질문 문구를 찾는다. */
public interface AskedQuestions {
    Map<String, String> of(long consultRequestId);

    static AskedQuestions none() {
        return consultRequestId -> Map.of();
    }
}
