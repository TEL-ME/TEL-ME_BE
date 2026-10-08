package com.telme.faq.dto.req;

//상담 경로의 검색 종류(FaqSearchAnswerProvider). 질문마다 첫 검색은 원문(ORIGINAL)이거나,
//이전 대화로 지시어를 풀어 쓴 질문(RESOLVED) 중 하나다. 첫 검색이 비면 정제 질문(REFINED)으로 한 번 더 검색한다
public enum FaqSearchKind {
    ORIGINAL, RESOLVED, REFINED
}
