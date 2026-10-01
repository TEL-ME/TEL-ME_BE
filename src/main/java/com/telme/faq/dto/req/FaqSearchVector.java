package com.telme.faq.dto.req;

// 검색에 쓸 벡터. 측정용 검색 테스트 API에서만 넘긴다(채팅·상담 경로는 주지 않아 설정을 따른다)
// QA: 질문+답변 벡터만, QUESTION: 질문만 벡터만, DUAL: 설정과 상관없이 두 결과를 합친다
public enum FaqSearchVector {
    QA, QUESTION, DUAL
}
