package com.telme.faq.dto.res;

import java.time.LocalDate;

public record FaqSearchResponse(
        Long faqId,
        // 내용이 바뀌어도 유지되는 FAQ 식별자(원본 JSON의 slot_id). 원본에 없는 FAQ는 null
        String slotId,
        String category,
        String question,
        String answer,
        double score,
        Integer version,
        LocalDate updatedAt,
        Integer searchRank
) {
}
