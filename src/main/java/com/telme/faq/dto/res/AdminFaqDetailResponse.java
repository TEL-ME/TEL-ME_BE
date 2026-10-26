package com.telme.faq.dto.res;

import java.time.Instant;

public record AdminFaqDetailResponse(
        Long faqId,
        String category,
        String question,
        String answer,
        String policyRef,
        Integer version,
        // 수정·삭제 요청에 그대로 돌려주면 화면을 연 뒤의 덮어쓰기를 막는다
        Integer lockVersion,
        String contentHash,
        String status,
        long citationCount,
        Long createdBy,
        Long updatedBy,
        Instant createdAt,
        Instant updatedAt
) {
}
