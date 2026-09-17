package com.telme.faq.dto.res;

import java.time.LocalDate;

public record FaqSearchResponse(
        Long faqId,
        String category,
        String question,
        String answer,
        double score,
        Integer version,
        LocalDate updatedAt,
        Integer searchRank
) {
}
