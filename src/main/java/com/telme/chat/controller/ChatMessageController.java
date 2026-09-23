package com.telme.chat.controller;

import com.telme.chat.dto.res.ChatMessageSourcesResponse;
import com.telme.chat.service.ChatActor;
import com.telme.chat.service.ChatActorProvider;
import com.telme.chat.service.ChatMessageSourceService;
import com.telme.global.common.CustomResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Chat Message", description = "채팅 메시지 부가 정보 API")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/chat/messages")
public class ChatMessageController {

    private final ChatActorProvider chatActorProvider;
    private final ChatMessageSourceService chatMessageSourceService;

    @Operation(summary = "답변 근거 조회", description = "특정 채팅 메시지에 저장된 FAQ 근거를 검색 순위대로 조회합니다.")
    @GetMapping("/{messageId}/sources")
    public CustomResponse<ChatMessageSourcesResponse> getMessageSources(
            HttpServletRequest servletRequest,
            @PathVariable
            @Positive(message = "메시지 ID는 1 이상이어야 합니다.")
            Long messageId
    ) {
        ChatActor actor = chatActorProvider.getCurrentActor(servletRequest);
        return CustomResponse.onSuccess(chatMessageSourceService.getMessageSources(actor, messageId));
    }
}
