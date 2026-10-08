package com.telme.intent.service;

// 지원하지 않거나 안전하게 분해하지 못한 질문을 부분 처리하지 않도록 알린다.
public final class UnsupportedCompoundQuestionException extends IllegalStateException {
    public UnsupportedCompoundQuestionException() {
        super("현재 Chat 상담 연결은 FAQ와 매장 복합 질문을 지원하지 않습니다.");
    }

    public UnsupportedCompoundQuestionException(String reason) {
        super(reason);
    }
}
