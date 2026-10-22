package com.telme.llm.service;

import com.telme.llm.dto.req.LlmRequest;

// 주 모델이 실패했을 때 대체 모델로 넘기지 않고 이 예외로 끝낼지 정한다
@FunctionalInterface
public interface FallbackGate {

    FallbackGate ALWAYS_FALLBACK = (request, failure) -> null;

    // null이면 대체 모델로 넘기고, 예외를 주면 대체하지 않고 그 예외로 실패시킨다
    RuntimeException blockFallback(LlmRequest request, Throwable failure);
}
