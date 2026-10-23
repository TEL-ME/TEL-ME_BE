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
        validateParts(payload.subQueries(), question);
    }

    // 하위 질문마다 현재 요청 안의 서로 다른 원문 근거가 있는지 확인한다. 없으면 UnsupportedCompoundQuestionException.
    static void validateParts(List<LlmRoutingPayload.SubQueryPayload> subQueries, String question) {
        String original = RoutingQuestionNormalizer.quoteKey(question);
        validateQuotes(subQueries.stream().map(sub -> evidenceKey(sub, original)).toList(), question);
    }

    // validateParts가 이 하위 질문의 근거로 쓰는 정규화 문자열.
    private static String evidenceKey(LlmRoutingPayload.SubQueryPayload sub, String original) {
        String quote = RoutingQuestionNormalizer.quoteKey(sub.requestQuote());
        if (!quote.isBlank() && isComposedOfOriginalSegments(quote, original)) {
            return quote;
        }
        // 원문 인용을 잘못 복사해도 실제 검색 질문이 원문 표현으로 구성되어 있으면 그 근거를 사용한다.
        String query = RoutingQuestionNormalizer.quoteKey(sub.queryText());
        return !query.isBlank() && isComposedOfOriginalSegments(query, original) ? query : quote;
    }

    static String verifiedQuote(LlmRoutingPayload.SubQueryPayload sub, String question) {
        String original = RoutingQuestionNormalizer.quoteKey(question);
        String quote = RoutingQuestionNormalizer.quoteKey(sub.requestQuote());
        if (!quote.isBlank() && original.contains(quote)) {
            return sub.requestQuote().strip();
        }
        String recovered = recoverParticleInsertion(original, quote);
        if (recovered != null) {
            return recovered;
        }
        String query = RoutingQuestionNormalizer.quoteKey(sub.queryText());
        if (!query.isBlank() && original.contains(query)) {
            return sub.queryText().strip();
        }
        throw new UnsupportedCompoundQuestionException("독립 요청의 원문 근거가 필요합니다.");
    }

    // 고객 원문에서 다른 하위 질문의 근거 구간만 공백으로 지운다. 이 요청의 구간과 어느 요청에도 속하지 않은 공통 구간이 남는다.
    // 원문 띄어쓰기는 유지한다. 구간을 확정하지 못하면 null을 돌려준다.
    static String withoutOtherQuotes(List<LlmRoutingPayload.SubQueryPayload> subQueries,
            LlmRoutingPayload.SubQueryPayload current, String question) {
        String normalized = RoutingQuestionNormalizer.quoteKey(question);
        int[][] spans = spans(subQueries, normalized);
        if (spans == null) {
            return null;
        }
        int[] positions = originalPositions(normalized, question);
        StringBuilder masked = new StringBuilder(question);
        for (int index = 0; index < subQueries.size(); index++) {
            if (subQueries.get(index) == current) {
                continue;
            }
            for (int character = positions[spans[index][0]]; character <= positions[spans[index][1] - 1]; character++) {
                masked.setCharAt(character, ' ');
            }
        }
        return masked.toString();
    }

    // 이 하위 질문의 근거가 차지하는 원문 구간. 인용에서 빠진 조사도 포함한다. 확정하지 못하면 null.
    static String ownSectionText(List<LlmRoutingPayload.SubQueryPayload> subQueries,
            LlmRoutingPayload.SubQueryPayload current, String question) {
        String normalized = RoutingQuestionNormalizer.quoteKey(question);
        int[][] spans = spans(subQueries, normalized);
        int index = -1;
        for (int candidate = 0; candidate < subQueries.size(); candidate++) {
            if (subQueries.get(candidate) == current) {
                index = candidate;
            }
        }
        if (spans == null || index < 0) {
            return null;
        }
        int[] positions = originalPositions(normalized, question);
        return question.substring(positions[spans[index][0]], positions[spans[index][1] - 1] + 1).strip();
    }

    // 하위 질문마다 근거가 정규화 원문에서 차지하는 [시작, 끝). validateQuotes처럼 입력 순서대로 찾는다.
    // 인용 검증과 같은 기준으로, 원문 조각을 순서대로 이어 맞춘 첫 조각부터 마지막 조각까지를 구간으로 본다.
    private static int[][] spans(List<LlmRoutingPayload.SubQueryPayload> subQueries, String original) {
        int[][] spans = new int[subQueries.size()][];
        int from = 0;
        for (int index = 0; index < subQueries.size(); index++) {
            String key = evidenceKey(subQueries.get(index), original);
            int[] span = key.isBlank() ? null : segmentSpan(key, original, from);
            if (span == null) {
                return null;
            }
            spans[index] = span;
            from = span[1];
        }
        return spans;
    }

    private static int[] segmentSpan(String key, String original, int from) {
        int keyOffset = 0;
        int originalOffset = from;
        int start = -1;
        while (keyOffset < key.length()) {
            int minimumLength = key.length() == 1 ? 1 : 2;
            boolean matched = false;
            for (int end = key.length(); end >= keyOffset + minimumLength; end--) {
                int position = original.indexOf(key.substring(keyOffset, end), originalOffset);
                if (position >= 0) {
                    start = start < 0 ? position : start;
                    originalOffset = position + end - keyOffset;
                    keyOffset = end;
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                return null;
            }
        }
        return new int[] {start, originalOffset};
    }

    // 띄어쓰기를 지운 인용으로 정한 검색어를 원문 띄어쓰기로 되돌린다. 붙여 쓴 검색어는 검색 점수가 크게 낮다
    // ("번이 철회하면 제 회선은 어떻게 돼요" 0.854 → 붙여 쓰면 0.740, TELME-225). 모델이 띄어 쓴 검색어와 원문에 없는 말은 그대로 둔다
    static String withOriginalSpacing(String text, String question) {
        if (text == null || text.isBlank() || question == null || text.strip().chars().anyMatch(Character::isWhitespace)) {
            return text;
        }
        String normalized = RoutingQuestionNormalizer.quoteKey(question);
        String key = RoutingQuestionNormalizer.quoteKey(text);
        int start = normalized.indexOf(key);
        if (key.isEmpty() || start < 0) {
            return text;
        }
        int[] positions = originalPositions(normalized, question);
        return question.substring(positions[start], positions[start + key.length() - 1] + 1).strip();
    }

    // 정규화 문자열의 각 글자가 원문의 몇 번째 글자인지. 정규화는 공백만 지우거나 하나로 줄인다.
    private static int[] originalPositions(String normalized, String question) {
        int[] positions = new int[normalized.length()];
        int cursor = 0;
        for (int index = 0; index < normalized.length(); index++) {
            char character = normalized.charAt(index);
            if (isSpace(character)) {
                while (cursor < question.length() && !isSpace(question.charAt(cursor))) {
                    cursor++;
                }
                positions[index] = cursor;
                while (cursor < question.length() && isSpace(question.charAt(cursor))) {
                    cursor++;
                }
                continue;
            }
            while (cursor < question.length() && question.charAt(cursor) != character) {
                cursor++;
            }
            positions[index] = cursor++;
        }
        return positions;
    }

    private static boolean isSpace(char character) {
        return Character.isWhitespace(character) || Character.isSpaceChar(character);
    }

    // 조사 한 글자만 빠진 인용은 원문에 실제로 이어진 구간으로 복구한다.
    private static String recoverParticleInsertion(String original, String quote) {
        if (quote.isBlank()) {
            return null;
        }
        for (int start = 0; start + quote.length() + 1 <= original.length(); start++) {
            String candidate = original.substring(start, start + quote.length() + 1);
            for (int offset = 1; offset < candidate.length() - 1; offset++) {
                char inserted = candidate.charAt(offset);
                if ("한는은이가을를의에와과".indexOf(inserted) >= 0
                        && (candidate.substring(0, offset) + candidate.substring(offset + 1)).equals(quote)) {
                    return candidate;
                }
            }
        }
        return null;
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
