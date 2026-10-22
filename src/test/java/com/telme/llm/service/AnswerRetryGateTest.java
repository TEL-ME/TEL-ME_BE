package com.telme.llm.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.telme.global.common.exception.GeneralException;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.exception.LlmErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class AnswerRetryGateTest {

    private final AnswerRetryGate gate = new AnswerRetryGate(executionId -> executionId == RETRIED_EXECUTION_ID);
    private final LlmRequest first = LlmRequest.builder().executionId(1L).userPrompt("u").build();
    private final LlmRequest afterRetry = first.toBuilder().executionId(RETRIED_EXECUTION_ID).build();
    private static final long RETRIED_EXECUTION_ID = 2L;

    @ParameterizedTest
    @EnumSource(value = LlmErrorCode.class,
            names = {"TIMEOUT", "TEMPORARILY_UNAVAILABLE", "CONNECTION_FAILED", "MODEL_ERROR", "INVALID_RESPONSE"})
    @DisplayName("첫 호출의 Bedrock 실패는 대체하지 않고 재시도 가능 실패로 끝낸다")
    void 첫_호출_실패는_재시도_가능_실패로_끝낸다(LlmErrorCode code) {
        RuntimeException blocked = gate.blockFallback(first, new GeneralException(code));

        assertThat(blocked).isInstanceOf(GeneralException.class);
        assertThat(((GeneralException) blocked).getErrorCode()).isEqualTo(LlmErrorCode.RETRYABLE_FAILURE);
    }

    @Test
    @DisplayName("요청·권한·설정 오류는 다시 해도 같으므로 바로 대체한다")
    void 요청_거부는_바로_대체한다() {
        assertThat(gate.blockFallback(first, new GeneralException(LlmErrorCode.REQUEST_REJECTED))).isNull();
    }

    @Test
    @DisplayName("사용자가 다시 시도한 호출이 또 실패하면 대체한다")
    void 사용자_재시도_뒤_실패는_대체한다() {
        assertThat(gate.blockFallback(afterRetry, new GeneralException(LlmErrorCode.TIMEOUT))).isNull();
    }

    @Test
    @DisplayName("실행 ID가 없는 호출은 첫 호출로 본다")
    void 실행_ID가_없으면_첫_호출이다() {
        LlmRequest noExecution = LlmRequest.builder().userPrompt("u").build();

        assertThat(gate.blockFallback(noExecution, new GeneralException(LlmErrorCode.TIMEOUT))).isNotNull();
    }

    @Test
    @DisplayName("LLM 오류가 아닌 예외는 기존처럼 대체한다")
    void LLM_오류가_아니면_대체한다() {
        assertThat(gate.blockFallback(first, new IllegalStateException("버그"))).isNull();
    }
}
