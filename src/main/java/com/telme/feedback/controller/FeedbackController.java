package com.telme.feedback.controller;

import com.telme.feedback.api.VerifiedFeedbackActorResolver;
import com.telme.feedback.converter.FeedbackConverter;
import com.telme.feedback.dto.FeedbackModels.Actor;
import com.telme.feedback.dto.req.FeedbackSaveRequest;
import com.telme.feedback.dto.res.FeedbackResponse;
import com.telme.feedback.exception.FeedbackErrorCode;
import com.telme.feedback.service.FeedbackService;
import com.telme.global.common.CustomResponse;
import com.telme.global.common.code.CommonErrorCode;
import com.telme.global.common.exception.GeneralException;

import io.swagger.v3.oas.annotations.Operation;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/chat/messages/{messageId}/feedback")
@ConditionalOnProperty(name = "telme.feedback.enabled", havingValue = "true")
@RequiredArgsConstructor
public class FeedbackController {
    private final FeedbackService service;
    private final VerifiedFeedbackActorResolver actors;
    private final FeedbackConverter converter;

    @PutMapping
    @Operation(summary = "내 메시지 피드백 등록 또는 수정")
    public CustomResponse<FeedbackResponse> save(
            @PathVariable long messageId,
            @Valid @RequestBody FeedbackSaveRequest request,
            HttpServletRequest httpRequest) {
        Actor actor = actor(httpRequest);
        checkId(messageId);
        return CustomResponse.onSuccess(
                converter.toResponse(service.save(messageId, actor, converter.toInput(request))));
    }

    @GetMapping
    @Operation(summary = "내 메시지 피드백 조회")
    public CustomResponse<FeedbackResponse> get(
            @PathVariable long messageId, HttpServletRequest request) {
        Actor actor = actor(request);
        checkId(messageId);
        return CustomResponse.onSuccess(
                service.get(messageId, actor).map(converter::toResponse).orElse(null));
    }

    @DeleteMapping
    @Operation(summary = "내 메시지 피드백 취소")
    public CustomResponse<Void> delete(@PathVariable long messageId, HttpServletRequest request) {
        Actor actor = actor(request);
        checkId(messageId);
        service.cancel(messageId, actor);
        return CustomResponse.onSuccess(null);
    }

    private Actor actor(HttpServletRequest request) {
        Actor actor = actors.resolve(request);
        if (actor == null) throw new GeneralException(CommonErrorCode.UNAUTHORIZED);
        return actor;
    }

    private void checkId(long id) {
        if (id <= 0) throw new GeneralException(FeedbackErrorCode.INVALID_REQUEST);
    }
}
