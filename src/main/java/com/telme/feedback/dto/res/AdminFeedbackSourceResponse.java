package com.telme.feedback.dto.res;

import java.math.BigDecimal;

// 답변을 만들 때 근거로 쓴 FAQ 한 건. 값은 그때 저장된 것이라 지금 FAQ와 다를 수 있다
public record AdminFeedbackSourceResponse(
        Long faqId,
        String titleSnapshot,
        Integer faqVersion,
        Short searchRank,
        BigDecimal score
) {
}
