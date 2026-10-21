package com.telme.llm.service;

import com.telme.global.common.exception.GeneralException;
import com.telme.llm.exception.LlmErrorCode;
import java.net.SocketTimeoutException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

// HTTP로 부르는 모델 서버(Ollama, vLLM)의 라이브러리 예외를 실패 원인별 오류 코드로 바꾼다. 중단 예외는 그대로 넘긴다
final class LlmHttpErrors {

    private LlmHttpErrors() {
    }

    static RuntimeException toLlmException(RuntimeException e) {
        if (e instanceof GeneralException) {
            return e;
        }
        if (e instanceof ResourceAccessException) {
            boolean timeout = e.getCause() instanceof SocketTimeoutException;
            return new GeneralException(
                    timeout ? LlmErrorCode.TIMEOUT : LlmErrorCode.CONNECTION_FAILED);
        }
        if (e instanceof RestClientResponseException) {
            return new GeneralException(LlmErrorCode.MODEL_ERROR);
        }
        return e;
    }
}
