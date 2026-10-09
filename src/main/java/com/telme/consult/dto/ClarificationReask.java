package com.telme.consult.dto;

import java.util.Objects;

/** 재질문 문구와 횟수 상한 */
public final class ClarificationReask {
    public static final int MAX_REASKS = 1;

    private static final String PREFIX = "답변을 잘 이해하지 못했어요. 다시 한 번 알려주세요.\n";

    private ClarificationReask() {}

    public static String text(String question) {
        return PREFIX + Objects.requireNonNull(question, "question");
    }
}
