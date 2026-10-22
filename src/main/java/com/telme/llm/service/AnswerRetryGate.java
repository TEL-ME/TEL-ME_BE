package com.telme.llm.service;

import com.telme.global.common.exception.GeneralException;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.exception.LlmCallException;
import com.telme.llm.exception.LlmErrorCode;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

// 답변 생성의 첫 Bedrock 실패는 엑사원 대신 사용자 재시도로 종료, 재시도한 실행의 실패와 요청 거부는 엑사원 대체
@Slf4j
public class AnswerRetryGate implements FallbackGate {

    private final UserRetryLookup userRetries;

    public AnswerRetryGate(UserRetryLookup userRetries) {
        this.userRetries = Objects.requireNonNull(userRetries);
    }

    @Override
    public RuntimeException blockFallback(LlmRequest request, Throwable failure) {
        if (!(failure instanceof GeneralException general)
                || general.getErrorCode() == LlmErrorCode.REQUEST_REJECTED
                || afterUserRetry(request)) {
            return null;
        }
        log.warn("[LLM 재시도 안내] 대체하지 않고 사용자 재시도로 넘깁니다. taskType={}, cause={}",
                request.taskType(), general.getErrorCode().getCode());
        return new LlmCallException(LlmErrorCode.RETRYABLE_FAILURE, general.getErrorCode().getCode());
    }

    private boolean afterUserRetry(LlmRequest request) {
        return request.executionId() != null && userRetries.afterUserRetry(request.executionId());
    }
}
