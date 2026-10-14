package com.telme.intent.service;

/**
 * LLM이 UNKNOWN으로 분류한 질문을 FAQ 검색으로 한 번 더 확인하는 경계다.
 * 통신 단어가 없는 질문(가상계좌, 위임장, 할부 이율 등)을 모델이 통신과 무관하다고 보는 경우를 되살린다.
 * 답변 검색과 같은 임계값을 통과한 FAQ가 있을 때만 true다.
 */
@FunctionalInterface
public interface UnknownFaqRecheck {
    boolean hasFaq(String question);

    static UnknownFaqRecheck none() {
        return question -> false;
    }
}
