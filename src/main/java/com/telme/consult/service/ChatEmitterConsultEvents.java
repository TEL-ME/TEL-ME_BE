package com.telme.consult.service;

import com.telme.chat.entity.ChatMessage;
import com.telme.chat.service.ChatEmitterRegistry;
import com.telme.chat.service.ChatExecutionState;
import com.telme.chat.service.ChatFailure;
import com.telme.llm.exception.LlmStreamCancelledException;
import com.telme.llm.service.LlmStreamHandler;
import com.telme.notification.dto.NotificationType;

import java.util.Objects;

import lombok.extern.slf4j.Slf4j;

/** 상담의 저장 완료 시점과 SSE 이벤트 전송을 연결한다. 답변 처리는 끝났지만 종료 결과를 전달하지 못하면 알림을 남긴다. */
@Slf4j
public final class ChatEmitterConsultEvents implements ConsultChatEvents {
    private final ChatEmitterRegistry emitters;
    private final UndeliveredAnswers undelivered;

    public ChatEmitterConsultEvents(ChatEmitterRegistry emitters) {
        this(emitters, (executionId, type) -> {});
    }

    public ChatEmitterConsultEvents(ChatEmitterRegistry emitters, UndeliveredAnswers undelivered) {
        this.emitters = Objects.requireNonNull(emitters);
        this.undelivered = Objects.requireNonNull(undelivered);
    }

    /** 처리는 끝났지만 종료 결과를 전하지 못한 실행(취소 제외). */
    @FunctionalInterface
    public interface UndeliveredAnswers {
        void save(long executionId, NotificationType type);
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
            public void onProgress() {
                if (!wasRegistered && emitters.isRegistered(executionId)) {
                    wasRegistered = true;
                }
                if (wasRegistered && !emitters.isRegistered(executionId)) {
                    throw new LlmStreamCancelledException();
                }
            }

            @Override
            public void onToken(String token) {
                onProgress();
                if (emitters.isRegistered(executionId)) {
                    if (!emitters.sendEvent(executionId, "token", token)) {
                        throw new LlmStreamCancelledException();
                    }
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

    // 늦은 구독이 먼저 결과를 받았으면 그쪽이 숨긴 알림 행을 남겨 두어, 여기서 저장해도 새 알림이 생기지 않는다(DB 고유 키)
    @Override
    public void completed(long executionId, ChatExecutionState state) {
        if (!emitters.complete(executionId, state)) {
            saveUndelivered(executionId, NotificationType.ANSWER_ARRIVED);
        }
    }

    @Override
    public void failed(long executionId, ChatFailure failure) {
        // 연결이 끊겨 취소된 답변은 사용자가 떠난 것이라 알리지 않는다
        if (!emitters.fail(executionId, failure) && failure.status() != ChatMessage.Status.CANCELLED) {
            saveUndelivered(executionId, NotificationType.ANSWER_FAILED);
        }
    }

    // 알림 저장 실패가 답변 완료 처리를 깨뜨리지 않게 한다
    private void saveUndelivered(long executionId, NotificationType type) {
        try {
            undelivered.save(executionId, type);
        } catch (RuntimeException exception) {
            log.warn("놓친 답변 알림 저장 실패: executionId={}, type={}", executionId, type, exception);
        }
    }
}
