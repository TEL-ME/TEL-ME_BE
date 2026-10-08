package com.telme.intent.service;

// 한 번에 처리할 수 있는 FAQ 하위 질문 수를 초과했다.
public final class TooManyFaqQuestionsException extends IllegalStateException {
    public TooManyFaqQuestionsException(int maximum) {
        super("FAQ 질문은 한 번에 최대 " + maximum + "개까지 처리할 수 있습니다.");
    }
}
