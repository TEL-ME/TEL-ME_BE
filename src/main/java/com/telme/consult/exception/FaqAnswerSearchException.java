package com.telme.consult.exception;

/** 검색 시스템 실패를 정상적인 빈 검색 결과와 구분하는 상담 경계 예외. */
public class FaqAnswerSearchException extends RuntimeException {
    public static final String ERROR_CODE = "FAQ_SEARCH_FAILED";

    public FaqAnswerSearchException(RuntimeException cause) {
        super("FAQ 답변 검색에 실패했습니다.", cause);
    }
}
