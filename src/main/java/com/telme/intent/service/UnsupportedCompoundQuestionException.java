package com.telme.intent.service;

/** FAQ와 매장이 섞인 질문을 상담 처리기가 부분 처리하지 않도록 알리는 예외다. */
public final class UnsupportedCompoundQuestionException extends IllegalStateException {
    public UnsupportedCompoundQuestionException() {
        super("현재 Chat 상담 연결은 FAQ와 매장 복합 질문을 지원하지 않습니다.");
    }

    public UnsupportedCompoundQuestionException(String reason) {
        super(reason);
    }
}
