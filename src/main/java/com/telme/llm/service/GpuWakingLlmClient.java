package com.telme.llm.service;

import com.telme.llm.dto.req.LlmRequest;
import lombok.RequiredArgsConstructor;

// 엑사원(vLLM) 호출이 들어오면 GPU EC2 기동을 먼저 요청
// 평소 엑사원은 Bedrock이 실패했을 때만 불리기 때문에 이 호출이 곧 "GPU가 필요하다"는 신호
// 기동 요청은 비동기라 호출 자체는 그대로 진행
@RequiredArgsConstructor
public class GpuWakingLlmClient implements LlmClient {

    private final LlmClient delegate;
    private final GpuInstanceStarter starter;

    @Override
    public String generate(LlmRequest request) {
        starter.requestStart();
        return delegate.generate(request);
    }

    @Override
    public void stream(LlmRequest request, LlmStreamHandler handler) {
        starter.requestStart();
        delegate.stream(request, handler);
    }
}
