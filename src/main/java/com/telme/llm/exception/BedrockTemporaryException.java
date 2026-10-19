package com.telme.llm.exception;

import com.telme.global.common.exception.GeneralException;

// 공개 오류 코드는 유지하면서 확인된 일시 장애만 대체 호출 대상으로 구분한다.
public class BedrockTemporaryException extends GeneralException {
    public BedrockTemporaryException(LlmErrorCode errorCode) {
        super(errorCode);
    }
}
