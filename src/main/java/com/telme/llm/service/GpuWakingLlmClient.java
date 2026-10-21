package com.telme.llm.service;

import com.telme.llm.dto.req.LlmRequest;
import lombok.RequiredArgsConstructor;

// Bedrock이 실패해 엑사원(vLLM)으로 대체된 호출이면 GPU EC2 기동기에 알린다
// 켤지는 기동기가 정한다(5분 안에 대체 2번). 일시 오류 한 번으로는 켜지 않고,
// 이어 묻는 질문, 대화 중간의 매장 질문처럼 턴당 대체가 1번인 경로도 두 번째 턴에 켜진다
// 조건 추출의 파싱 재시도(parseFallback)처럼 엑사원을 직접 부르는 호출은 대체가 아니라 세지 않는다(LlmFallbackScope)
// 기동 요청은 비동기라 호출 자체는 그대로 진행
@RequiredArgsConstructor
public class GpuWakingLlmClient implements LlmClient {

    private final LlmClient delegate;
    private final GpuInstanceStarter starter;

    @Override
    public String generate(LlmRequest request) {
        wakeIfFallback();
        return delegate.generate(request);
    }

    @Override
    public void stream(LlmRequest request, LlmStreamHandler handler) {
        wakeIfFallback();
        delegate.stream(request, handler);
    }

    private void wakeIfFallback() {
        if (LlmFallbackScope.active()) {
            starter.requestStart();
        }
    }
}
