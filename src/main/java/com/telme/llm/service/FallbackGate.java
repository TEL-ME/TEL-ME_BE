package com.telme.llm.service;

import com.telme.llm.dto.req.LlmRequest;

@FunctionalInterface
public interface FallbackGate {

    FallbackGate ALWAYS_FALLBACK = (request, failure) -> null;

    // null이면 대체 모델 호출, 예외면 대체 없이 그 예외로 실패
    RuntimeException blockFallback(LlmRequest request, Throwable failure);
}
