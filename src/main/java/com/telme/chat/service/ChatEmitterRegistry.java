package com.telme.chat.service;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import lombok.extern.slf4j.Slf4j;

// executionId 하나당 구독 연결(SseEmitter) 하나만 지원한다. 같은 실행을 여러 탭에서 동시에 구독하면
// 나중에 연결한 쪽이 먼저 연결한 쪽을 registry에서 덮어쓴다 — 다중 구독자 지원은 이번 범위 밖.
@Slf4j
@Component
public class ChatEmitterRegistry {

    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    public void register(Long executionId, SseEmitter emitter) {
        emitters.put(executionId, emitter);
        emitter.onCompletion(() -> emitters.remove(executionId, emitter));
        emitter.onTimeout(() -> {
            emitters.remove(executionId, emitter);
            emitter.complete();
        });
        emitter.onError(exception -> emitters.remove(executionId, emitter));
    }

    public boolean isRegistered(Long executionId) {
        return emitters.containsKey(executionId);
    }

    public void sendEvent(Long executionId, String eventName, Object payload) {
        SseEmitter emitter = emitters.get(executionId);
        if (emitter == null) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name(eventName).data(payload));
        } catch (IOException exception) {
            log.info("SSE 전송 실패로 구독 정리: executionId={}", executionId, exception);
            emitters.remove(executionId, emitter);
        }
    }

    public void complete(Long executionId, Object payload) {
        SseEmitter emitter = emitters.remove(executionId);
        if (emitter == null) {
            return;
        }
        sendQuietly(emitter, "complete", payload);
        emitter.complete();
    }

    public void fail(Long executionId, Object payload) {
        SseEmitter emitter = emitters.remove(executionId);
        if (emitter == null) {
            return;
        }
        sendQuietly(emitter, "error", payload);
        emitter.complete();
    }

    // 구독을 걸기 전에 이미 끝나 있던 실행을 위한 즉시 종료 경로 — registry에는 등록하지 않는다.
    public void sendTerminalNow(SseEmitter emitter, String eventName, Object payload) {
        sendQuietly(emitter, eventName, payload);
        emitter.complete();
    }

    private void sendQuietly(SseEmitter emitter, String eventName, Object payload) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(payload));
        } catch (IOException exception) {
            log.info("SSE 종료 이벤트 전송 실패", exception);
        }
    }
}
