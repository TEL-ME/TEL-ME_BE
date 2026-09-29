package com.telme.faq.dto.res;

import java.time.Instant;

public record AdminFaqDetailResponse(
        Long faqId,
        String category,
        String question,
        String answer,
        String policyRef,
        Integer version,
        String contentHash,
        String status,
        long citationCount,
        Long createdBy,
        Long updatedBy,
        Instant createdAt,
        Instant updatedAt
) {
}
