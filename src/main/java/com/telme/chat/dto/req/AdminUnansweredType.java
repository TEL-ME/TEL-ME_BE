package com.telme.chat.dto.req;

// 근거를 못 찾은 답변과 답변을 끝내지 못한 경우는 다른 컬럼에 남아 한 값으로 묶을 수 없다
public enum AdminUnansweredType {
    NO_EVIDENCE,
    OUT_OF_SCOPE,
    FAILED,
    TIMEOUT;

    public boolean isBasis() {
        return this == NO_EVIDENCE || this == OUT_OF_SCOPE;
    }
}
