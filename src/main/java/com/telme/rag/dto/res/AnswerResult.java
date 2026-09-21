package com.telme.rag.dto.res;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import lombok.Builder;

@Builder
public record AnswerResult(
        String answer,
        List<AnswerSource> sources
) {

    public AnswerResult {
        if (sources == null) {
            sources = List.of();
        }
    }

    @Builder
    public record AnswerSource(
            Long faqId,
            String titleSnapshot,
            Integer faqVersion,
            LocalDate faqUpdatedAt,
            Short searchRank,
            BigDecimal score
    ) {
    }
}
