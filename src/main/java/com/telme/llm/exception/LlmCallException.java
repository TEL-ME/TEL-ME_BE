package com.telme.llm.exception;

import com.telme.global.common.exception.GeneralException;

// LLM 호출 실패에 원인 상세(Bedrock 종료 사유, AWS 오류 코드 등) 추가
public class LlmCallException extends GeneralException {

    private final String detail;

    public LlmCallException(LlmErrorCode errorCode, String detail) {
        super(errorCode);
        this.detail = detail;
    }

    @Override
    public String getMessage() {
        String base = getErrorCode().getCode() + " " + getErrorCode().getMessage();
        return detail == null || detail.isBlank() ? base : base + " (" + detail + ")";
    }
}
