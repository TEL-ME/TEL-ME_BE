package com.telme.rag.exception;

import com.telme.global.common.exception.GeneralException;
import com.telme.llm.exception.LlmErrorCode;

// 호출 기록의 error_message로 남아 가드 발동을 모델 오류와 구분한다
public class AnswerGuardException extends GeneralException {

    // 무엇 때문에 막았는지. 금액(AMOUNT)이면 답변 생성이 추론 강도를 올려 한 번 다시 부른다(TELME-205)
    public enum Kind {
        AMOUNT,
        MEASURE
    }

    private final String reason;
    private final Kind kind;

    public AnswerGuardException(String reason, Kind kind) {
        super(LlmErrorCode.INVALID_RESPONSE);
        this.reason = reason;
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    @Override
    public String getMessage() {
        return reason;
    }
}
