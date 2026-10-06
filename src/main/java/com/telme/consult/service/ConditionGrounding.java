package com.telme.consult.service;

import com.telme.faq.dto.res.FaqSearchResponse;
import java.util.List;
import java.util.Set;

/** 뽑은 조건이 FAQ 답변에 실제로 있는지 본다. 답변(A)만 근거로 치고 질문(Q)은 보지 않는다. */
final class ConditionGrounding {

    // 예·아니요는 정책이 아니라 묻는 방식이라 근거에 적혀 있지 않다
    private static final Set<String> YES_NO = Set.of("예", "네", "아니요", "아니오");

    private ConditionGrounding() {}

    static String sourceText(List<FaqSearchResponse> sources) {
        return sources.stream()
                .map(FaqSearchResponse::answer)
                .filter(answer -> answer != null && !answer.isBlank())
                .map(ConditionGrounding::compact)
                .reduce("", String::concat);
    }

    static boolean grounded(String text, String sourceText) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return sourceText.contains(compact(text));
    }

    static List<String> groundedOptions(List<String> options, String sourceText) {
        if (options.stream().allMatch(option -> YES_NO.contains(option.strip()))) {
            return options;
        }
        return options.stream().filter(option -> grounded(option, sourceText)).toList();
    }

    private static String compact(String text) {
        return text.replaceAll("[\\s\\p{Punct}]", "").toLowerCase();
    }
}
