package com.telme.chat.dto.res;

import java.math.BigDecimal;

// 답을 만들 때 저장된 근거 기록 한 건. 값은 그때 저장된 것이라 지금 FAQ와 다를 수 있다
public record AdminUnansweredSourceResponse(
        Long faqId,
        String titleSnapshot,
        Integer faqVersion,
        Short searchRank,
        BigDecimal score
) {
}
