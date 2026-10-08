package com.telme.intent.service;

import com.telme.intent.dto.res.LlmRoutingPayload;
import com.telme.intent.entity.QueryRouting.Intent;
import java.util.List;

// 분해된 질문마다 현재 고객 요청의 서로 다른 원문 구간이 있는지 확인한다.
final class IndependentQuestionPolicy {
    private IndependentQuestionPolicy() {}

    static void validate(LlmRoutingPayload payload, String question) {
        if (payload.intent() != Intent.FAQ
                || payload.subQueries().size() <= 1) {
            return;
        }
        validateQuotes(payload.subQueries().stream().map(LlmRoutingPayload.SubQueryPayload::requestQuote).toList(),
                question);
    }

    static void validateQuotes(List<String> quotes, String question) {
        String original = RoutingQuestionNormalizer.quoteKey(question);
        int previousEnd = 0;
        for (String selection : quotes) {
            String quote = RoutingQuestionNormalizer.quoteKey(selection);
            int start = original.indexOf(quote, previousEnd);
            if (quote.isBlank() || start < 0) {
                throw new UnsupportedCompoundQuestionException(
                        "여러 질문을 현재 요청의 독립적인 원문 구간에 연결할 수 없습니다.");
            }
            previousEnd = start + quote.length();
        }
    }

    static void validateAgainstInventory(LlmRoutingPayload payload, String question, List<String> inventory) {
        validate(payload, question);
        if (inventory.size() != payload.subQueries().size()) {
            throw new UnsupportedCompoundQuestionException("질문 분해와 독립 요청 판정이 일치하지 않습니다.");
        }
        for (int index = 0; index < inventory.size(); index++) {
            String planned = RoutingQuestionNormalizer.quoteKey(inventory.get(index));
            String selected = RoutingQuestionNormalizer.quoteKey(payload.subQueries().get(index).requestQuote());
            if (!planned.contains(selected) && !selected.contains(planned)) {
                throw new UnsupportedCompoundQuestionException("질문별 원문 구간이 독립 요청과 일치하지 않습니다.");
            }
        }
    }
}
