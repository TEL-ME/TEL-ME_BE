package com.telme.chat.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

public record ChatMessageSourceResponse(
        @Schema(description = "답변 생성 당시 참조한 FAQ ID")
        Long faqId,
        @Schema(description = "답변 생성 당시 FAQ 질문 제목 스냅샷")
        String title,
        @Schema(description = "검색 결과 순위")
        Short searchRank,
        @Schema(description = "검색 시점의 유사도 점수이며 모델 변경에 따라 분포가 달라질 수 있음", example = "0.9123")
        BigDecimal score
) {
}
