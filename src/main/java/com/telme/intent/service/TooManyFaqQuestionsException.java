package com.telme.intent.service;

// FAQ와 매장을 합쳐 한 번에 처리할 수 있는 하위 요청 수를 초과했다.
public final class TooManyFaqQuestionsException extends IllegalStateException {
    public TooManyFaqQuestionsException(int maximum) {
        super("질문은 한 번에 최대 " + maximum + "개까지 처리할 수 있습니다.");
    }
}
