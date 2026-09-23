package com.telme.chat.service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// ChatEmitterRegistry(구독 중인 SSE 연결)로 이벤트를 보내는 ChatStreamPublisher 구현.
// 이벤트 이름과 payload는 구독 API(ChatExecutionController)의 즉시 종료 경로와 같게 맞춘다.
@Component
@RequiredArgsConstructor
public class SseChatStreamPublisher implements ChatStreamPublisher {

    static final String START = "start";
    static final String TOKEN = "token";
    static final String STATUS = "status";
    static final String RETRYING = "RETRYING";

    private final ChatEmitterRegistry emitterRegistry;

    // 클라이언트는 메시지 전송 응답을 받은 뒤에 구독하므로 첫 토큰이 구독보다 먼저 나올 수 있다.
    // 구독 전에는 토큰을 버리고 생성을 이어가고, 한 번 붙었던 구독이 사라졌을 때만 이탈로 본다.
    private final Set<Long> subscribedExecutions = ConcurrentHashMap.newKeySet();

    @Override
    public void publishStarted(Long executionId, ChatExecutionState state) {
        send(executionId, START, state);
    }

    @Override
    public boolean publishToken(Long executionId, String token) {
        if (send(executionId, TOKEN, token)) {
            return true;
        }
        return !subscribedExecutions.contains(executionId);
    }

    @Override
    public void publishRetrying(Long executionId) {
        send(executionId, STATUS, RETRYING);
    }

    @Override
    public void publishCompleted(Long executionId, ChatExecutionState state) {
        subscribedExecutions.remove(executionId);
        emitterRegistry.complete(executionId, state);
    }

    @Override
    public void publishFailed(Long executionId, ChatFailure failure) {
        subscribedExecutions.remove(executionId);
        emitterRegistry.fail(executionId, failure);
    }

    private boolean send(Long executionId, String eventName, Object payload) {
        if (!emitterRegistry.isRegistered(executionId)) {
            return false;
        }
        subscribedExecutions.add(executionId);
        return emitterRegistry.sendEvent(executionId, eventName, payload);
    }
}
