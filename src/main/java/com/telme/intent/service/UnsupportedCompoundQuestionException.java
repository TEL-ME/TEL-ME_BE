package com.telme.intent.service;

/** 단일 상담 처리기가 복합 질문을 부분 처리하지 않도록 알리는 예외다. */
public final class UnsupportedCompoundQuestionException extends IllegalStateException {
    public UnsupportedCompoundQuestionException() {
        super("현재 Chat 상담 연결은 단일 하위 질문만 지원합니다.");
    }
}
