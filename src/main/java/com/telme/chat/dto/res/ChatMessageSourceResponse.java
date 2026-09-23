package com.telme.chat.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

public record ChatMessageSourceResponse(
        Long faqId,
        String title,
        Short searchRank,
        @Schema(description = "검색 시점의 유사도 점수", example = "0.9123")
        BigDecimal score
) {
}
