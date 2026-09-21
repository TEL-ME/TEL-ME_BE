package com.telme.intent.controller;

import com.telme.chat.entity.ChatMessage;
import com.telme.chat.entity.ChatSession;
import com.telme.chat.repository.ChatMessageRepository;
import com.telme.chat.service.ChatActor;
import com.telme.chat.service.ChatActorProvider;
import com.telme.global.common.CustomResponse;
import com.telme.global.common.code.CommonErrorCode;
import com.telme.global.common.exception.GeneralException;
import com.telme.intent.dto.req.IntentRouteRequest;
import com.telme.intent.dto.res.IntentRouteResponse;
import com.telme.intent.exception.IntentErrorCode;
import com.telme.intent.service.QueryRoutingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Intent Route", description = "질문 의도 라우팅 및 복합 질의 분해 API")
@RestController
@RequestMapping("/api/v1/intent-routes")
@RequiredArgsConstructor
public class IntentController {

    private final QueryRoutingService queryRoutingService;
    private final ChatMessageRepository chatMessageRepository;
    private final ObjectProvider<ChatActorProvider> chatActorProvider;

    @Operation(summary = "질문 의도 라우팅 및 복합 질의 분해")
    @PostMapping
    public CustomResponse<IntentRouteResponse> route(
            HttpServletRequest servletRequest,
            @Valid @RequestBody IntentRouteRequest request
    ) {
        if (request == null) {
            throw new IllegalArgumentException("요청 본문은 필수입니다.");
        }

        ChatMessage message = chatMessageRepository.findByIdWithSession(request.messageId())
            .orElseThrow(() -> new GeneralException(IntentErrorCode.MESSAGE_NOT_FOUND));

        validateOwnership(servletRequest, message);

        IntentRouteResponse response = queryRoutingService.route(message);
        return CustomResponse.onSuccess(response);
    }

    private void validateOwnership(HttpServletRequest servletRequest, ChatMessage message) {
        ChatActorProvider actorProvider = chatActorProvider.getIfAvailable();
        if (actorProvider == null) {
            return;
        }
        ChatActor actor = actorProvider.getCurrentActor(servletRequest);
        ChatSession chatSession = message.getSession();
        if (actor != null && chatSession != null) {
            boolean isOwner = actor.isMember()
                ? (chatSession.getUserId() != null && chatSession.getUserId().equals(actor.userId()))
                : (chatSession.getUserId() == null && actor.guestId() != null && actor.guestId().equals(chatSession.getGuestId()));
            if (!isOwner) {
                throw new GeneralException(CommonErrorCode.FORBIDDEN);
            }
        }
    }
}
