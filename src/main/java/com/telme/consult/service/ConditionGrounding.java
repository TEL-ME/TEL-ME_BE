package com.telme.consult.service;

import com.telme.faq.dto.res.FaqSearchResponse;
import java.util.List;
import java.util.Set;

/** 뽑은 조건이 FAQ 답변에 실제로 있는지 본다. 답변(A)만 근거로 치고 질문(Q)은 보지 않는다. */
final class ConditionGrounding {

    // 예·아니요는 정책이 아니라 묻는 방식이라 근거에 적혀 있지 않다
    private static final Set<String> YES_NO = Set.of("예", "네", "아니요", "아니오");

    // 모델이 근거를 조금씩 바꿔 쓴다. 이어지는 글자가 이만큼 겹치면 그 문장을 보고 쓴 것으로 친다
    private static final int MIN_OVERLAP = 8;

    private ConditionGrounding() {}

    static String sourceText(List<FaqSearchResponse> sources) {
        return sources.stream()
                .map(FaqSearchResponse::answer)
                .filter(answer -> answer != null && !answer.isBlank())
                .map(ConditionGrounding::compact)
                .reduce("", String::concat);
    }

    static boolean grounded(String evidence, String sourceText) {
        if (evidence == null || evidence.isBlank()) {
            return false;
        }
        String compacted = compact(evidence);
        if (compacted.length() <= MIN_OVERLAP) {
            return sourceText.contains(compacted);
        }
        return longestOverlap(compacted, sourceText) >= MIN_OVERLAP;
    }

    static List<String> groundedOptions(List<String> options, String sourceText) {
        if (options.stream().allMatch(option -> YES_NO.contains(option.strip()))) {
            return options;
        }
        // 선택지는 근거에 적힌 값 그대로여야 한다. 바꿔 쓰면 고객이 고른 값이 검색에 안 걸린다
        return options.stream()
                .filter(option -> !option.isBlank() && sourceText.contains(compact(option)))
                .toList();
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
