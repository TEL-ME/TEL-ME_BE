package com.telme.intent.controller;

import com.telme.chat.entity.ChatMessage;
import com.telme.global.common.CustomResponse;
import com.telme.intent.dto.req.IntentRouteRequest;
import com.telme.intent.dto.res.IntentRouteResponse;
import com.telme.intent.service.QueryRoutingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/intent-routes")
@RequiredArgsConstructor
public class IntentController {

    private final QueryRoutingService queryRoutingService;

    @PostMapping
    public CustomResponse<IntentRouteResponse> route(@Valid @RequestBody IntentRouteRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("요청 본문은 필수입니다.");
        }

        ChatMessage transientMessage = ChatMessage.builder()
            .messageId(request.messageId())
            .content(request.content())
            .build();

        IntentRouteResponse response = queryRoutingService.route(transientMessage);
        return CustomResponse.onSuccess(response);
    }
}
