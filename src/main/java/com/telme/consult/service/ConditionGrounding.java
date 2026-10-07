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

    // 범위를 가르는 말과 숫자가 바뀌면 뜻이 달라진다. "14세 미만"과 "14세 이상"은 반대다
    private static final Pattern BOUNDARY = Pattern.compile("미만|이상|이하|초과|\\d+");

    /** 근거로 인정된 FAQ 문장과 그 문장이 나온 FAQ. */
    record Grounded(String sentence, FaqSearchResponse source) {}

    private ConditionGrounding() {}

    /** 모델이 가리킨 FAQ 답변 문장. 찾지 못하면 null이고, 찾으면 모델 문장 대신 이 원문을 쓴다. */
    static Grounded groundedEvidence(String evidence, List<FaqSearchResponse> sources) {
        if (evidence == null || evidence.isBlank()) {
            return null;
        }
        Grounded found = null;
        int best = 0;
        // 근거를 여러 문장에 걸쳐 옮겨 쓰기도 해서 양쪽 다 문장 단위로 맞춘다
        for (String quoted : split(evidence)) {
            String compacted = compact(quoted);
            if (compacted.isEmpty()) {
                continue;
            }
            int required = Math.min(MIN_OVERLAP, compacted.length());
            for (FaqSearchResponse source : sources) {
                for (String sentence : sentences(source)) {
                    int overlap = longestOverlap(compacted, compact(sentence));
                    if (overlap >= required && overlap > best && sameMeaning(compacted, compact(sentence))) {
                        best = overlap;
                        found = new Grounded(sentence, source);
                    }
                }
            }
        }
        return found;
    }

    // 부정과 범위가 다르면 뜻이 뒤집힌 것이라 근거로 쓰지 않는다
    private static boolean sameMeaning(String compacted, String sentence) {
        if (negated(compacted) != negated(sentence)) {
            return false;
        }
        return BOUNDARY.matcher(compacted).results()
                .map(java.util.regex.MatchResult::group)
                .allMatch(sentence::contains);
    }

    private static List<String> sentences(FaqSearchResponse source) {
        return source.answer() == null ? List.of() : split(source.answer());
    }

    private static List<String> split(String text) {
        return Arrays.stream(text.split("(?<=[.!?])\\s+|\\n"))
                .map(String::strip)
                .filter(sentence -> !sentence.isEmpty())
                .toList();
    }

    private static boolean negated(String compacted) {
        return NEGATION.matcher(compacted).find();
    }

    static List<String> groundedOptions(List<String> options, FaqSearchResponse source) {
        if (options.stream().allMatch(option -> YES_NO.contains(option.strip()))) {
            return options;
        }
        String sourceText = source.answer() == null ? "" : compact(source.answer());
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
