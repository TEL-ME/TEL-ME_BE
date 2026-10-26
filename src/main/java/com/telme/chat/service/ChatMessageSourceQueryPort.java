package com.telme.chat.service;

import java.math.BigDecimal;
import java.util.List;

public interface ChatMessageSourceQueryPort {

    List<Source> findByMessageId(Long messageId);

    record Source(
            Long faqId,
            String title,
            Short searchRank,
            BigDecimal score
    ) {
    }
}
