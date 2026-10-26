package com.telme.rag.dto.res;

import com.telme.chat.entity.ChatMessage.AnswerBasis;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import lombok.Builder;

@Builder
public record AnswerResult(
        String answer,
        // 답변 메시지 저장 시 chat이 그대로 사용
        AnswerBasis answerBasis,
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
