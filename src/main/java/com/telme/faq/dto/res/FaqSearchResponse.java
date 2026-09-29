package com.telme.faq.dto.res;

import com.telme.faq.service.FaqEmbeddingTextVariant;
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
        Integer searchRank,
        // 어느 벡터로 찾았는지. 이중 벡터 검색에서 두 점수(분포가 다름)를 구분할 때 쓴다. 벡터 검색이 아니면 null
        FaqEmbeddingTextVariant matchedVariant
) {
}
