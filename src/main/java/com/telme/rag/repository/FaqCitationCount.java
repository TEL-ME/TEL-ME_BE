package com.telme.rag.repository;

// FAQ가 답변 근거로 쓰인 횟수. 0건인 FAQ는 집계에 안 잡히므로 조회한 쪽에서 0으로 채운다
public record FaqCitationCount(Long faqId, Long citationCount) {
}
