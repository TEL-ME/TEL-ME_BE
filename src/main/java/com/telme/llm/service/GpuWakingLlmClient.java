package com.telme.llm.service;

import com.telme.llm.dto.req.LlmRequest;
import lombok.RequiredArgsConstructor;

// 엑사원(vLLM) 호출이 들어오면 GPU EC2 기동기에 알린다
// 평소 엑사원은 Bedrock 오류나 조건 추출 파싱 실패 때만 불린다. 켤지는 기동기가 호출 빈도로 정함(짧은 시간에 여러 번이면 장애)
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
