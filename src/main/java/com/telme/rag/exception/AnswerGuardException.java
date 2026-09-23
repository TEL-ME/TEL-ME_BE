package com.telme.rag.exception;

import com.telme.global.common.exception.GeneralException;
import com.telme.llm.exception.LlmErrorCode;

// 호출 기록의 error_message로 남아 가드 발동을 모델 오류와 구분한다
public class AnswerGuardException extends GeneralException {

    private final String reason;

    public AnswerGuardException(String reason) {
        super(LlmErrorCode.INVALID_RESPONSE);
        this.reason = reason;
    }

    @Override
    public String getMessage() {
        return reason;
    }
}
