package com.telme.consult.dto;

import java.util.List;
import java.util.regex.Pattern;

/** 근거 확인까지 끝난 되물을 조건. options가 비면 사용자가 직접 입력한다. */
public record MissingCondition(String key, String question, List<String> options, String evidence) {

    // consult_conditions.condition_key가 VARCHAR(50)
    private static final Pattern KEY = Pattern.compile("[a-z][a-z0-9_]{0,49}");

    public MissingCondition {
        if (key == null || !KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("조건 이름이 올바르지 않습니다.");
        }
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("되물을 질문이 필요합니다.");
        }
        options = options == null ? List.of() : List.copyOf(options);
        question = question.strip();
    }

    public boolean selectable() {
        return !options.isEmpty();
    }
}
