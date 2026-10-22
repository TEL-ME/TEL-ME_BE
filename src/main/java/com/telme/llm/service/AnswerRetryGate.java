package com.telme.llm.service;

import com.telme.global.common.exception.GeneralException;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.exception.LlmCallException;
import com.telme.llm.exception.LlmErrorCode;
import lombok.extern.slf4j.Slf4j;

// 답변 생성에서 Bedrock이 실패하면 곧바로 엑사원으로 넘기지 않고, 사용자가 다시 시도할 수 있는 실패로 끝낸다
// 사용자가 다시 시도한 호출(afterUserRetry)이 또 실패하면 그때 엑사원으로 대체한다
// 요청·권한·설정 오류는 다시 해도 같은 결과라 버튼 없이 바로 대체한다. LLM 오류가 아닌 예외도 기존처럼 대체한다
@Slf4j
public class AnswerRetryGate implements FallbackGate {

    @Override
    public RuntimeException blockFallback(LlmRequest request, Throwable failure) {
        if (request.afterUserRetry() || !(failure instanceof GeneralException general)) {
            return null;
        }
        if (general.getErrorCode() == LlmErrorCode.REQUEST_REJECTED) {
            return null;
        }
        log.warn("[LLM 재시도 안내] 대체하지 않고 사용자 재시도로 넘깁니다. taskType={}, cause={}",
                request.taskType(), general.getErrorCode().getCode());
        return new LlmCallException(LlmErrorCode.RETRYABLE_FAILURE, general.getErrorCode().getCode());
    }
}
