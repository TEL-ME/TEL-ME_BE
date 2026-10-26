package com.telme.faq.dto.res;

import java.time.Instant;

public record AdminFaqListItemResponse(
        Long faqId,
        String category,
        String question,
        Integer version,
        String status,
        long citationCount,
        Instant updatedAt
) {
}
