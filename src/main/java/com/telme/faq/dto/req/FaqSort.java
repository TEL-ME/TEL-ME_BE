package com.telme.faq.dto.req;

public enum FaqSort {
    // 답변 근거로 많이 쓰인 순. 같으면 FAQ ID 순
    POPULAR,
    // 최근 수정 순. 같으면 FAQ ID 역순
    RECENT
}
