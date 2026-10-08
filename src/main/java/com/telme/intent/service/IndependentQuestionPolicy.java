package com.telme.intent.service;

import com.telme.intent.dto.res.LlmRoutingPayload;
import com.telme.intent.entity.QueryRouting.Intent;
import java.util.List;

// 분해된 질문마다 현재 요청의 서로 다른 근거가 있는지 확인한다.
final class IndependentQuestionPolicy {
    private IndependentQuestionPolicy() {}

    static void validate(LlmRoutingPayload payload, String question) {
        if (payload.intent() != Intent.FAQ
                || payload.subQueries().size() <= 1) {
            return;
        }
        String original = RoutingQuestionNormalizer.quoteKey(question);
        validateQuotes(payload.subQueries().stream().map(sub -> {
            String quote = RoutingQuestionNormalizer.quoteKey(sub.requestQuote());
            if (!quote.isBlank() && isComposedOfOriginalSegments(quote, original)) {
                return quote;
            }
            // 원문 인용을 잘못 복사해도 실제 검색 질문이 원문 표현으로 구성되어 있으면 그 근거를 사용한다.
            String query = RoutingQuestionNormalizer.quoteKey(sub.queryText());
            return !query.isBlank() && isComposedOfOriginalSegments(query, original) ? query : quote;
        }).toList(), question);
    }

    static String verifiedQuote(LlmRoutingPayload.SubQueryPayload sub, String question) {
        String original = RoutingQuestionNormalizer.quoteKey(question);
        String quote = RoutingQuestionNormalizer.quoteKey(sub.requestQuote());
        if (!quote.isBlank() && original.contains(quote)) {
            return sub.requestQuote().strip();
        }
        String query = RoutingQuestionNormalizer.quoteKey(sub.queryText());
        if (!query.isBlank() && original.contains(query)) {
            return sub.queryText().strip();
        }
        throw new UnsupportedCompoundQuestionException("독립 요청의 원문 근거가 필요합니다.");
    }

    static void validateQuotes(List<String> quotes, String question) {
        String original = RoutingQuestionNormalizer.quoteKey(question);
        List<String> normalized = quotes.stream().map(RoutingQuestionNormalizer::quoteKey).toList();
        if (normalized.size() < 2 || normalized.stream().anyMatch(String::isBlank)) {
            throw new UnsupportedCompoundQuestionException("독립 요청의 원문 근거가 필요합니다.");
        }
        if (normalized.stream().allMatch(original::contains)) {
            validateLiteralQuotes(normalized, original);
            return;
        }

        int previousEnd = 0;
        for (int index = 0; index < normalized.size(); index++) {
            String quote = normalized.get(index);
            if (!isComposedOfOriginalSegments(quote, original)) {
                throw new UnsupportedCompoundQuestionException(
                        "여러 질문에 원문에 없는 내용이 포함됐습니다.");
            }
            SourceAnchor anchor = findDistinctAnchor(normalized, index, original, previousEnd);
            if (anchor == null) {
                throw new UnsupportedCompoundQuestionException(
                        "여러 질문을 서로 다른 원문 근거에 연결할 수 없습니다.");
            }
            previousEnd = anchor.end();
        }
    }

    private static void validateLiteralQuotes(List<String> quotes, String original) {
        int previousEnd = 0;
        for (String selection : quotes) {
            int start = original.indexOf(selection, previousEnd);
            if (start < 0) {
                throw new UnsupportedCompoundQuestionException(
                        "여러 질문을 현재 요청의 독립적인 원문 구간에 연결할 수 없습니다.");
            }
            previousEnd = start + selection.length();
        }
    }

    private static boolean isComposedOfOriginalSegments(String quote, String original) {
        int quoteOffset = 0;
        int originalOffset = 0;
        while (quoteOffset < quote.length()) {
            int minimumLength = quote.length() == 1 ? 1 : 2;
            boolean matched = false;
            for (int end = quote.length(); end >= quoteOffset + minimumLength; end--) {
                int start = original.indexOf(quote.substring(quoteOffset, end), originalOffset);
                if (start >= 0) {
                    originalOffset = start + end - quoteOffset;
                    quoteOffset = end;
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                return false;
            }
        }
        return true;
    }

    private static SourceAnchor findDistinctAnchor(List<String> quotes, int index,
            String original, int previousEnd) {
        String quote = quotes.get(index);
        for (int length = quote.length(); length >= (quote.length() == 1 ? 1 : 2); length--) {
            for (int offset = 0; offset + length <= quote.length(); offset++) {
                String fragment = quote.substring(offset, offset + length);
                boolean shared = false;
                for (int other = 0; other < quotes.size(); other++) {
                    if (other != index && quotes.get(other).contains(fragment)) {
                        shared = true;
                        break;
                    }
                }
                if (shared) {
                    continue;
                }
                int position = original.indexOf(fragment, previousEnd);
                if (position >= 0) {
                    return new SourceAnchor(position, position + length);
                }
            }
        }
        return null;
    }

    private record SourceAnchor(int start, int end) {}
}
