package com.telme.chat.controller;

import com.telme.chat.config.ChatExecutionProperties;
import com.telme.chat.dto.req.ChatExecutionRetryRequest;
import com.telme.chat.dto.res.ChatExecutionTraceResponse;
import com.telme.chat.dto.res.ChatMessageSendResponse;
import com.telme.chat.entity.ChatExecution;
import com.telme.chat.exception.ChatErrorCode;
import com.telme.chat.service.ChatActor;
import com.telme.chat.service.ChatActorProvider;
import com.telme.chat.service.ChatEmitterRegistry;
import com.telme.chat.service.ChatExecutionState;
import com.telme.chat.service.ChatFailure;
import com.telme.chat.service.ChatResponseStopwatch;
import com.telme.chat.service.ChatSessionService;
import com.telme.chat.service.ChatExecutionTraceService;
import com.telme.global.common.CustomResponse;
import com.telme.chat.entity.ChatMessage;
import com.telme.global.common.exception.GeneralException;
import com.telme.notification.service.NotificationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import java.time.Duration;
import java.util.function.BooleanSupplier;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

// SSE 엔드포인트는 SseEmitter를 직접 반환해야 하므로 기존 API의 공통 응답 컨벤션을 따르기 어렵습니다.
// 따라서 다른 API들과 섞이지 않도록 독립된 컨트롤러로 분리하여 관리합니다.
@Slf4j
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
    private final ChatExecutionTraceService traces;
    private final ObjectProvider<NotificationService> notifications;

    @Operation(summary = "실행 처리 흐름 조회", description = "본인 대화의 검색 근거와 처리 결과를 실행 ID로 조회합니다.")
    @GetMapping("/{executionId}/trace")
    public CustomResponse<ChatExecutionTraceResponse> trace(
            HttpServletRequest request,
            @PathVariable Long sessionId,
            @PathVariable Long executionId) {
        return CustomResponse.onSuccess(
                traces.get(chatActorProvider.getCurrentActor(request), sessionId, executionId));
    }

    @Operation(summary = "답변 다시 시도",
            description = "답변 생성에 실패해 다시 시도할 수 있는(error 이벤트의 retryable=true, 새로고침 후에는 "
                    + "메시지 이력의 retryableExecutionId) 실행을 같은 질문으로 다시 처리합니다. 질문은 새로 저장하지 않고 "
                    + "새 실행 ID를 돌려주므로 그 ID로 구독합니다. 질문마다 한 번만 가능하고, 이 시도가 또 실패하면 "
                    + "다른 모델로 대체해 답합니다. 원래 질문에 위치를 보냈다면 같은 위치를 본문으로 다시 보냅니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "새 실행 시작"),
            @ApiResponse(responseCode = "404", description = "CHAT404-0/CHAT404-1: 세션 또는 실행을 찾을 수 없음"),
            @ApiResponse(responseCode = "409", description = "CHAT409-3: 다른 답변이 생성 중, "
                    + "CHAT409-5: 다시 시도할 수 없는 답변, CHAT409-6: 이미 다시 시도한 질문")
    })
    @PostMapping("/{executionId}/retry")
    public ResponseEntity<CustomResponse<ChatMessageSendResponse>> retry(
            HttpServletRequest servletRequest,
            @PathVariable Long sessionId,
            @PathVariable Long executionId,
            @Valid @RequestBody(required = false) ChatExecutionRetryRequest request) {
        ChatResponseStopwatch stopwatch = ChatResponseStopwatch.start();
        ChatActor actor = chatActorProvider.getCurrentActor(servletRequest);
        ChatMessageSendResponse response =
                chatSessionService.retryExecution(actor, sessionId, executionId, request, stopwatch);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CustomResponse.onSuccess(HttpStatus.CREATED, response));
    }

    // front작업시 이벤트 별로 emitterRegistry를 확인해서 어떤식으로 token단위로 오는지 확인하기
    @Operation(summary = "AI 답변 실행 구독", description = "실행 중인 답변 생성 과정을 SSE로 실시간 구독합니다. "
                    + "complete 이벤트의 outputMessage.handoff가 true이면 FAQ 근거를 찾지 못한 답변이라 상담원 연결을 안내할 수 있습니다. 저장하지 않는 값이라 히스토리 조회에는 없습니다.")
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

        // [프론트엔드 연동 주의사항]
        // 구독 요청 시점에 이미 AI 답변이 종료(COMPLETED/FAILED)된 상태라면, 아래 로직에 의해
        // 터미널 이벤트(complete/error)를 전송한 직후 연결이 닫히게 됩니다.
        // 브라우저의 EventSource는 서버가 연결을 닫으면 자동으로 재연결을 시도하므로 무한 재연결 루프에 빠질 수 있습니다.
        // 따라서 프론트엔드에서는 'complete'나 'error' 이벤트를 수신하면 반드시 eventSource.close()를 명시적으로 호출해야 합니다.
        SseEmitter emitter = new SseEmitter(timeoutMillis());
        if (state.status() != ChatExecution.Status.RUNNING) {
            // Fast-path: 구독을 걸기 전에 이미 끝난 실행이면 즉시 종료 처리합니다.
            deliverLate(executionId, () -> {
                if (state.status() == ChatExecution.Status.COMPLETED) {
                    return emitterRegistry.sendTerminalNow(emitter, "complete", state);
                }
                ChatFailure chatFailure = new ChatFailure(ChatMessage.Status.valueOf(state.status().name()), state.errorCode());
                return emitterRegistry.sendTerminalNow(emitter, "error", chatFailure);
            });
            return emitter;
        }

        // 1. 상태 체크 전에 일단 무조건 구독 맵에 등록부터 합니다. (위에서 Fast-path 통과한 경우)
        emitterRegistry.register(executionId, emitter);

        // 구독 등록 후 상태를 다시 확인하여, 등록 직전에 AI 처리가 끝난 경우(TOCTOU 경쟁 상태)
        // 클라이언트가 이벤트를 받지 못하고 무한 대기하는 현상을 방지합니다.
        ChatExecutionState currentState = chatSessionService.getExecution(actor, executionId);
        if (currentState.status() != ChatExecution.Status.RUNNING) {
            // 이미 등록된 상태이므로 registry를 통해 이벤트를 보내고 제거 및 종료합니다.
            // 상담 처리 쪽이 먼저 보냈으면 여기서는 false라 알림을 건드리지 않습니다.
            deliverLate(executionId, () -> {
                if (currentState.status() == ChatExecution.Status.COMPLETED) {
                    return emitterRegistry.complete(executionId, currentState);
                }
                ChatFailure chatFailure = new ChatFailure(ChatMessage.Status.valueOf(currentState.status().name()), currentState.errorCode());
                return emitterRegistry.fail(executionId, chatFailure);
            });
        }

        return emitter;
    }

    // 결과를 전했을 때만 서버 알림을 숨긴다. 상담 처리 완료와 순서가 엇갈려도 DB 알림 행으로 맞춰진다
    private void deliverLate(Long executionId, BooleanSupplier send) {
        if (send.getAsBoolean()) {
            dismissQuietly(executionId);
        }
    }

    // 알림 숨김 실패가 이미 보낸 결과의 응답을 오류로 바꾸지 않게 한다. 실패하면 서버 알림이 남을 뿐이다
    private void dismissQuietly(Long executionId) {
        try {
            notifications.ifAvailable(service -> service.dismissDelivered(executionId));
        } catch (RuntimeException exception) {
            log.warn("늦은 구독 알림 숨김 실패: executionId={}", executionId, exception);
        }
    }

    private long timeoutMillis() {
        Duration timeout = chatExecutionProperties.runningTimeout().minus(TIMEOUT_MARGIN);
        return (timeout.compareTo(MIN_TIMEOUT) > 0 ? timeout : MIN_TIMEOUT).toMillis();
    }
}
