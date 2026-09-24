package com.telme.consult.service;

import com.telme.chat.service.ChatEmitterRegistry;
import com.telme.chat.service.ChatExecutionState;
import com.telme.chat.service.ChatFailure;
import com.telme.llm.exception.LlmStreamCancelledException;
import com.telme.llm.service.LlmStreamHandler;

import java.util.Objects;

/** 상담의 저장 완료 시점과 SSE 이벤트 전송을 연결한다. */
public final class ChatEmitterConsultEvents implements ConsultChatEvents {
    private final ChatEmitterRegistry emitters;

    public ChatEmitterConsultEvents(ChatEmitterRegistry emitters) {
        this.emitters = Objects.requireNonNull(emitters);
    }

    @Override
    public void started(ChatExecutionState state) {
        Objects.requireNonNull(state, "state");
        emitters.sendEvent(state.executionId(), "start", state);
    }

    @Override
    public LlmStreamHandler stream(long executionId) {
        return new LlmStreamHandler() {
            private boolean wasRegistered = emitters.isRegistered(executionId);

            @Override
            public void onToken(String token) {
                if (!wasRegistered && emitters.isRegistered(executionId)) {
                    wasRegistered = true;
                }
                if (emitters.isRegistered(executionId)) {
                    if (!emitters.sendEvent(executionId, "token", token)) {
                        throw new LlmStreamCancelledException();
                    }
                } else if (wasRegistered) {
                    throw new LlmStreamCancelledException();
                }
            }

            @Override
            public void onComplete() {
                // 최종 답변과 상담 상태 저장 뒤 completed()에서 종료 이벤트를 보낸다.
            }

            @Override
            public void onError(Throwable error) {
                // 실패 상태 저장 뒤 failed()에서 오류 이벤트를 보낸다.
            }

            @Override
            public void onRetry(int attempt, Throwable cause) {
                emitters.sendEvent(executionId, "status", "RETRYING");
            }
        };
    }

    @Override
    public void completed(long executionId, ChatExecutionState state) {
        emitters.complete(executionId, state);
    }

    @Override
    public void failed(long executionId, ChatFailure failure) {
        emitters.fail(executionId, failure);
    }
}
