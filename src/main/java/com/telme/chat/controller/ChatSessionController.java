package com.telme.chat.controller;

import com.telme.chat.dto.req.ChatMessageSendRequest;
import com.telme.chat.dto.req.ChatSessionCreateRequest;
import com.telme.chat.dto.req.ChatSessionTitleUpdateRequest;
import com.telme.chat.dto.res.ChatMessageSendResponse;
import com.telme.chat.dto.res.ChatSessionCreateResponse;
import com.telme.chat.dto.res.ChatSessionListResponse;
import com.telme.chat.dto.res.ChatSessionUpdateResponse;
import com.telme.chat.service.ChatActor;
import com.telme.chat.service.ChatActorProvider;
import com.telme.chat.service.ChatSessionService;
import com.telme.global.common.CustomResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
