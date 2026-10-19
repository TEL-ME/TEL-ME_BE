package com.telme.chat.service;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
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

    // 늦은 전달 기록 보관 시간. 실행 시간 초과보다 길게 둔다
    private static final Duration DELIVERED_LATE_TTL = Duration.ofMinutes(10);
    private static final int TERMINAL_LOCK_STRIPES = 64;

    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();
    // 늦게 구독한 화면에 종료 결과를 먼저 전한 실행
    private final Map<Long, Instant> deliveredLate = new ConcurrentHashMap<>();
    // 실행별 종료 처리(전송 + 알림 판단) 잠금
    private final Object[] terminalLocks = new Object[TERMINAL_LOCK_STRIPES];

    public ChatEmitterRegistry() {
        for (int i = 0; i < terminalLocks.length; i++) {
            terminalLocks[i] = new Object();
        }
    }

    public void register(Long executionId, SseEmitter emitter) {
        SseEmitter previous = emitters.put(executionId, emitter);
        if (previous != null) {
            log.info("새로운 SSE 구독으로 기존 연결 명시적 종료: executionId={}", executionId);
            try {
                // 이전 연결 탭에게 강제 종료됨을 알림 (프론트에서 eventSource.close()를 호출하여 무한 재연결 방지)
                previous.send(SseEmitter.event().name("error").data("CONNECTED_ELSEWHERE"));
            } catch (IOException | IllegalStateException ignored) {
                // 이미 끊어져 전송할 수 없는 경우 무시
            } finally {
                previous.complete();
            }
        }
        
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

    public boolean sendEvent(Long executionId, String eventName, Object payload) {
        SseEmitter emitter = emitters.get(executionId);
        if (emitter == null) {
            return false;
        }
        try {
            emitter.send(SseEmitter.event().name(eventName).data(payload));
            return true;
        } catch (IOException | IllegalStateException exception) {
            log.info("SSE 전송 실패로 구독 정리: executionId={}", executionId, exception);
            emitters.remove(executionId, emitter);
            return false;
        }
    }

    // 보냈으면 true, 구독자가 없거나 전송 실패면 false
    public boolean complete(Long executionId, Object payload) {
        return terminate(executionId, "complete", payload);
    }

    public boolean fail(Long executionId, Object payload) {
        return terminate(executionId, "error", payload);
    }

    private boolean terminate(Long executionId, String eventName, Object payload) {
        SseEmitter emitter = emitters.remove(executionId);
        if (emitter == null) {
            return false;
        }
        boolean delivered = sendQuietly(emitter, eventName, payload);
        emitter.complete();
        return delivered;
    }

    // 같은 실행의 종료 처리는 한 번에 하나만
    public void withTerminalLock(Long executionId, Runnable action) {
        synchronized (terminalLocks[Math.floorMod(executionId.hashCode(), terminalLocks.length)]) {
            action.run();
        }
    }

    public void markDeliveredLate(Long executionId) {
        Instant now = Instant.now();
        deliveredLate.values().removeIf(markedAt -> markedAt.isBefore(now.minus(DELIVERED_LATE_TTL)));
        deliveredLate.put(executionId, now);
    }

    // 늦게 구독한 화면에 이미 전한 실행이면 true. 확인하면 기록을 지운다
    public boolean consumeDeliveredLate(Long executionId) {
        return deliveredLate.remove(executionId) != null;
    }

    // 구독을 걸기 전에 이미 끝나 있던 실행을 위한 즉시 종료 경로 — registry에는 등록하지 않는다. 보냈으면 true
    public boolean sendTerminalNow(SseEmitter emitter, String eventName, Object payload) {
        boolean delivered = sendQuietly(emitter, eventName, payload);
        emitter.complete();
        return delivered;
    }

    private boolean sendQuietly(SseEmitter emitter, String eventName, Object payload) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(payload));
            return true;
        } catch (IOException | IllegalStateException exception) {
            log.info("SSE 종료 이벤트 전송 실패", exception);
            return false;
        }
    }
}
