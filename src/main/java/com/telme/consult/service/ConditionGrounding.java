package com.telme.consult.service;

import com.telme.faq.dto.res.FaqSearchResponse;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** 뽑은 조건이 FAQ 답변에 실제로 있는지 본다. 답변(A)만 근거로 치고 질문(Q)은 보지 않는다. */
final class ConditionGrounding {

    // 예·아니요는 정책이 아니라 묻는 방식이라 근거에 적혀 있지 않다
    private static final Set<String> YES_NO = Set.of("예", "네", "아니요", "아니오");

    // 모델이 근거를 조금씩 바꿔 쓴다. 이어지는 글자가 이만큼 겹치면 그 문장을 보고 쓴 것으로 친다
    private static final int MIN_OVERLAP = 8;

    private static final Pattern NEGATION = Pattern.compile("없|못|불가|아니");

    private ConditionGrounding() {}

    static String sourceText(List<FaqSearchResponse> sources) {
        return sources.stream()
                .map(FaqSearchResponse::answer)
                .filter(answer -> answer != null && !answer.isBlank())
                .map(ConditionGrounding::compact)
                .reduce("", String::concat);
    }

    /** 모델이 가리킨 FAQ 답변 문장. 찾지 못하면 null이고, 찾으면 모델 문장 대신 이 원문을 쓴다. */
    static String groundedSentence(String evidence, List<FaqSearchResponse> sources) {
        if (evidence == null || evidence.isBlank()) {
            return null;
        }
        String compacted = compact(evidence);
        if (compacted.isEmpty()) {
            return null;
        }
        int required = Math.min(MIN_OVERLAP, compacted.length());
        boolean negated = negated(compacted);
        String found = null;
        int best = 0;
        for (String sentence : sentences(sources)) {
            int overlap = longestOverlap(compacted, compact(sentence));
            // 여러 문장에 걸쳐 옮겨 쓰면 겹침이 가장 긴 문장이 뜻이 다른 쪽일 수 있어 뜻이 같은 문장만 고른다
            if (overlap >= required && overlap > best && negated(compact(sentence)) == negated) {
                best = overlap;
                found = sentence;
            }
        }
        return found;
    }

    private static List<String> sentences(List<FaqSearchResponse> sources) {
        return sources.stream()
                .map(FaqSearchResponse::answer)
                .filter(answer -> answer != null && !answer.isBlank())
                .flatMap(answer -> Arrays.stream(answer.split("(?<=[.!?])\\s+|\\n")))
                .map(String::strip)
                .filter(sentence -> !sentence.isEmpty())
                .toList();
    }

    private static boolean negated(String compacted) {
        return NEGATION.matcher(compacted).find();
    }

    static List<String> groundedOptions(List<String> options, String sourceText) {
        if (options.stream().allMatch(option -> YES_NO.contains(option.strip()))) {
            return options;
        }
        // 선택지는 근거에 적힌 값 그대로여야 한다. 바꿔 쓰면 고객이 고른 값이 검색에 안 걸린다
        return options.stream().filter(option -> containsValue(sourceText, compact(option))).toList();
    }

    // 11500원 안의 1500원처럼 숫자 중간에 걸린 값은 다른 값이다
    private static boolean containsValue(String sourceText, String value) {
        if (value.isEmpty()) {
            return false;
        }
        for (int from = sourceText.indexOf(value); from >= 0; from = sourceText.indexOf(value, from + 1)) {
            int end = from + value.length();
            boolean leftOk = from == 0 || !Character.isDigit(sourceText.charAt(from - 1));
            boolean rightOk = end == sourceText.length() || !Character.isDigit(sourceText.charAt(end));
            if (leftOk && rightOk) {
                return true;
            }
        }
        return false;
    }

    private static int longestOverlap(String text, String sourceText) {
        int[] previous = new int[sourceText.length() + 1];
        int longest = 0;
        for (int i = 1; i <= text.length(); i++) {
            int[] current = new int[sourceText.length() + 1];
            for (int j = 1; j <= sourceText.length(); j++) {
                if (text.charAt(i - 1) == sourceText.charAt(j - 1)) {
                    current[j] = previous[j - 1] + 1;
                    longest = Math.max(longest, current[j]);
                }
            }
            previous = current;
        }
        return longest;
    }

    private static String compact(String text) {
        return text.replaceAll("[\\s\\p{Punct}]", "").toLowerCase();
    }
}
