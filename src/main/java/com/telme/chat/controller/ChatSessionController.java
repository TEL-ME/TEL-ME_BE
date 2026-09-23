package com.telme.chat.controller;

import com.telme.chat.dto.req.ChatMessageSendRequest;
import com.telme.chat.dto.req.ChatSessionCreateRequest;
import com.telme.chat.dto.req.ChatSessionTitleUpdateRequest;
import com.telme.chat.dto.res.ChatMessageHistoryResponse;
import com.telme.chat.dto.res.ChatMessageSendResponse;
import com.telme.chat.dto.res.ChatMessageSourcesResponse;
import com.telme.chat.dto.res.ChatSessionCreateResponse;
import com.telme.chat.dto.res.ChatSessionListResponse;
import com.telme.chat.dto.res.ChatSessionUpdateResponse;
import com.telme.chat.service.ChatActor;
import com.telme.chat.service.ChatActorProvider;
import com.telme.chat.service.ChatSessionService;
import com.telme.global.common.CustomResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Chat Session", description = "채팅 세션과 사용자 메시지 API")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/chat/sessions")
public class ChatSessionController {

    private final ChatActorProvider chatActorProvider;
    private final ChatSessionService chatSessionService;

    @Operation(summary = "채팅 세션 생성")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomResponse<ChatSessionCreateResponse> createSession(
            HttpServletRequest servletRequest,
            @Valid @RequestBody ChatSessionCreateRequest request
    ) {
        ChatActor actor = chatActorProvider.getCurrentActor(servletRequest);
        ChatSessionCreateResponse response = chatSessionService.createSession(actor, request);
        return CustomResponse.onSuccess(HttpStatus.CREATED, response);
    }

    @Operation(summary = "내 채팅 세션 목록 조회")
    @GetMapping
    public CustomResponse<ChatSessionListResponse> getSessions(
            HttpServletRequest servletRequest,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "조회 개수는 1 이상이어야 합니다.")
            @Max(value = 50, message = "조회 개수는 50 이하여야 합니다.")
            int size
    ) {
        ChatActor actor = chatActorProvider.getCurrentActor(servletRequest);
        return CustomResponse.onSuccess(chatSessionService.getSessions(actor, cursor, size));
    }

    @Operation(
            summary = "대화 이력 조회",
            description = "최신 메시지 구간을 조회하며 응답은 sequenceNo 오름차순입니다. "
                    + "hasOlderMessages가 true이면 nextBeforeSequenceNo를 다음 요청에 넣어 더 과거 이력을 조회합니다."
    )
    @GetMapping("/{sessionId}/messages")
    public CustomResponse<ChatMessageHistoryResponse> getMessages(
            HttpServletRequest servletRequest,
            @PathVariable Long sessionId,
            @Parameter(
                    description = "더 과거 이력을 조회할 때 이전 응답의 nextBeforeSequenceNo 값을 사용합니다. "
                            + "첫 조회에서는 생략합니다."
            )
            @RequestParam(required = false)
            @Positive(message = "메시지 커서는 1 이상이어야 합니다.")
            Integer beforeSequenceNo,
            @Parameter(description = "조회할 메시지 수 (1~50)")
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "조회 개수는 1 이상이어야 합니다.")
            @Max(value = 50, message = "조회 개수는 50 이하여야 합니다.")
            int size
    ) {
        ChatActor actor = chatActorProvider.getCurrentActor(servletRequest);
        return CustomResponse.onSuccess(
                chatSessionService.getMessages(actor, sessionId, beforeSequenceNo, size)
        );
    }

    @Operation(summary = "답변 근거 조회", description = "특정 채팅 메시지에 저장된 FAQ 근거를 검색 순위대로 조회합니다.")
    @GetMapping("/{sessionId}/messages/{messageId}/sources")
    public CustomResponse<ChatMessageSourcesResponse> getMessageSources(
            HttpServletRequest servletRequest,
            @PathVariable Long sessionId,
            @PathVariable Long messageId
    ) {
        ChatActor actor = chatActorProvider.getCurrentActor(servletRequest);
        return CustomResponse.onSuccess(
                chatSessionService.getMessageSources(actor, sessionId, messageId)
        );
    }

    @Operation(summary = "채팅 세션 제목 변경")
    @PatchMapping("/{sessionId}/title")
    public CustomResponse<ChatSessionUpdateResponse> updateTitle(
            HttpServletRequest servletRequest,
            @PathVariable Long sessionId,
            @Valid @RequestBody ChatSessionTitleUpdateRequest request
    ) {
        ChatActor actor = chatActorProvider.getCurrentActor(servletRequest);
        return CustomResponse.onSuccess(chatSessionService.updateTitle(actor, sessionId, request));
    }

    @Operation(summary = "채팅 세션 종료")
    @PatchMapping("/{sessionId}/close")
    public CustomResponse<ChatSessionUpdateResponse> closeSession(
            HttpServletRequest servletRequest,
            @PathVariable Long sessionId
    ) {
        ChatActor actor = chatActorProvider.getCurrentActor(servletRequest);
        return CustomResponse.onSuccess(chatSessionService.closeSession(actor, sessionId));
    }

    @Operation(summary = "사용자 메시지 전송")
    @PostMapping("/{sessionId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public CustomResponse<ChatMessageSendResponse> sendMessage(
            HttpServletRequest servletRequest,
            @PathVariable Long sessionId,
            @Valid @RequestBody ChatMessageSendRequest request
    ) {
        ChatActor actor = chatActorProvider.getCurrentActor(servletRequest);
        ChatMessageSendResponse response = chatSessionService.sendMessage(actor, sessionId, request);
        return CustomResponse.onSuccess(HttpStatus.CREATED, response);
    }
}
