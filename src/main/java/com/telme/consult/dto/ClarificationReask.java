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

    // 복합 질문의 되묻기 메시지는 앞부분에 답변이 있고, 질문은 마지막 줄이다
    public static String questionOf(String content) {
        String stripped = Objects.requireNonNull(content, "content").strip();
        int lastBreak = stripped.lastIndexOf('\n');
        return lastBreak < 0 ? stripped : stripped.substring(lastBreak + 1).strip();
    }
}
