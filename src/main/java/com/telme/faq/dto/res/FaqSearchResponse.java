package com.telme.faq.dto.res;

public record FaqSearchResponse(
        Long faqId,
        String question,
        String answer,
        double score,
        Integer version
) {
}
