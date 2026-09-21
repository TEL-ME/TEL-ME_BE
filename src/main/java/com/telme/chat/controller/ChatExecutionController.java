package com.telme.chat.controller;

import com.telme.chat.config.ChatExecutionProperties;
import com.telme.chat.entity.ChatExecution;
import com.telme.chat.exception.ChatErrorCode;
import com.telme.chat.service.ChatActor;
import com.telme.chat.service.ChatActorProvider;
import com.telme.chat.service.ChatEmitterRegistry;
import com.telme.chat.service.ChatExecutionState;
import com.telme.chat.service.ChatSessionService;
import com.telme.global.common.exception.GeneralException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.servlet.http.HttpServletRequest;

import java.time.Duration;

import lombok.RequiredArgsConstructor;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

// SSE 엔드포인트는 SseEmitter를 직접 반환해야 하므로 기존 API의 공통 응답 컨벤션을 따르기 어렵습니다.
// 따라서 다른 API들과 섞이지 않도록 독립된 컨트롤러로 분리하여 관리합니다.
@Tag(name = "Chat Execution", description = "AI 응답 실행 구독")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/chat/sessions/{sessionId}/executions")
public class ChatExecutionController {

    // SSE 타임아웃은 반드시 실행 타임아웃(ChatExecutionProperties.runningTimeout)보다 짧아야 한다.
    // 그래야 이 구독 연결이 먼저 정리되고, 실행 타임아웃 스케줄러는 안전망으로만 남는다.
    private static final Duration TIMEOUT_MARGIN = Duration.ofSeconds(30);
    private static final Duration MIN_TIMEOUT = Duration.ofSeconds(10);

    private final ChatActorProvider chatActorProvider;
    private final ChatSessionService chatSessionService;
    private final ChatEmitterRegistry emitterRegistry;
    private final ChatExecutionProperties chatExecutionProperties;

    // front작업시 이벤트 별로 emitterRegistry를 확인해서 어떤식으로 token단위로 오는지 확인하기
    @Operation(summary = "AI 답변 실행 구독", description = "실행 중인 답변 생성 과정을 SSE로 실시간 구독합니다.")
    @GetMapping(value = "/{executionId}/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(
            HttpServletRequest servletRequest,
            @PathVariable Long sessionId,
            @PathVariable Long executionId) {
        ChatActor actor = chatActorProvider.getCurrentActor(servletRequest);
        ChatExecutionState state = chatSessionService.getExecution(actor, executionId);
        if (!state.sessionId().equals(sessionId)) {
            throw new GeneralException(ChatErrorCode.EXECUTION_NOT_FOUND);
        }

        SseEmitter emitter = new SseEmitter(timeoutMillis());
        if (state.status() != ChatExecution.Status.RUNNING) {
            // 구독을 걸기 전에 이미 끝난 실행이면, 지금 상태를 바로 흘려보내고 연결을 닫는다.
            String eventName = state.status() == ChatExecution.Status.COMPLETED ? "complete" : "error";
            emitterRegistry.sendTerminalNow(emitter, eventName, state);
            return emitter;
        }

        emitterRegistry.register(executionId, emitter);
        return emitter;
    }

    private long timeoutMillis() {
        Duration timeout = chatExecutionProperties.runningTimeout().minus(TIMEOUT_MARGIN);
        return (timeout.compareTo(MIN_TIMEOUT) > 0 ? timeout : MIN_TIMEOUT).toMillis();
    }
}
