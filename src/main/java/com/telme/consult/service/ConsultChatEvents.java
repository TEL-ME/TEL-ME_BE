package com.telme.consult.service;

import com.telme.chat.service.ChatExecutionState;
import com.telme.chat.service.ChatFailure;
import com.telme.llm.service.LlmStreamHandler;

/** 상담 처리 결과를 SSE 같은 외부 전달 수단에 알리는 경계다. DB 저장은 호출 전에 끝나야 한다. */
public interface ConsultChatEvents {
    void started(ChatExecutionState state);

    LlmStreamHandler stream(long executionId);

    void completed(long executionId, ChatExecutionState state);

    void failed(long executionId, ChatFailure failure);

    static ConsultChatEvents noop() {
        return new ConsultChatEvents() {
            @Override
            public void started(ChatExecutionState state) {}

            @Override
            public LlmStreamHandler stream(long executionId) {
                return new LlmStreamHandler() {
                    @Override
                    public void onToken(String token) {}

                    @Override
                    public void onComplete() {}

                    @Override
                    public void onError(Throwable error) {}
                };
            }

            @Override
            public void completed(long executionId, ChatExecutionState state) {}

            @Override
            public void failed(long executionId, ChatFailure failure) {}
        };
    }
}
