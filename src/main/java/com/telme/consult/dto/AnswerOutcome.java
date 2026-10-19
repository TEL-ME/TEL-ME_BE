package com.telme.consult.dto;

import java.util.Objects;

/**
 * 하위 질문 하나에 대해 고객에게 실제로 보인 결과. 답변을 만든 뒤 기록만 하며 답변 내용을 바꾸지 않는다.
 * text는 이 하위 질문에 대해 보인 문구이고, ASKED면 물어본 질문이다. displayQuestion은 복합 답변 소제목이며
 * 단일 질문이면 null이다. 상담 종료 요약이 읽는다(V41).
 */
public record AnswerOutcome(Kind kind, String text, String displayQuestion) {
    public enum Kind { ANSWERED, STORES, NO_EVIDENCE, ASKED, SPLIT_REFUSED }

    public AnswerOutcome {
        Objects.requireNonNull(kind, "kind");
    }

    public static AnswerOutcome asked(String question, String displayQuestion) {
        return new AnswerOutcome(Kind.ASKED, question, displayQuestion);
    }
}
