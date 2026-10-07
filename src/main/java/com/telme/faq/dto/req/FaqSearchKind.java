package com.telme.faq.dto.req;

// 상담 경로의 검색 종류. 원문 검색이 비면 정제 질문으로 한 번 더 검색한다(FaqSearchAnswerProvider)
public enum FaqSearchKind {
    ORIGINAL, REFINED
}
