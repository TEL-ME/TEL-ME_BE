package com.telme.intent.dto.res;

// 요청 관계와 개수만 판정하며 하위 질문이나 검색어는 생성하지 않는다.
public record LlmRequestInventoryPayload(Decision decision, int requestCount) {
    public enum Decision {
        SINGLE, COMPARISON, MULTIPLE
    }

}
