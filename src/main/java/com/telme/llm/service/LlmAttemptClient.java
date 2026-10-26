package com.telme.llm.service;

import com.telme.llm.dto.req.LlmRequest;

// 재시도 계층이 몇 번째 시도인지 알려줄 수 있는 내부 계약. 호출 기록에 시도 횟수를 남기기 위해 쓴다
public interface LlmAttemptClient extends LlmClient {

    String generate(LlmRequest request, int attempt);

    void stream(LlmRequest request, LlmStreamHandler handler, int attempt);

    @Override
    default String generate(LlmRequest request) {
        return generate(request, 1);
    }

    @Override
    default void stream(LlmRequest request, LlmStreamHandler handler) {
        stream(request, handler, 1);
    }
}
