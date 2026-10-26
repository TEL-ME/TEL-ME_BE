package com.telme.llm.service;

import com.telme.llm.dto.req.LlmRequest;

public interface LlmClient {

    // 실패 시 예외 발생
    String generate(LlmRequest request);

    // 완료될 때까지 blocking. 실패는 예외가 아니라 onError로 전달
    void stream(LlmRequest request, LlmStreamHandler handler);
}
