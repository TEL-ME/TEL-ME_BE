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
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Feedback", description = "상담 답변에 대한 내 평가 등록, 조회, 수정, 취소")
@RequestMapping("/api/v1/chat/messages/{messageId}/feedback")
@ConditionalOnProperty(name = "telme.feedback.enabled", havingValue = "true")
@RequiredArgsConstructor
public class FeedbackController {
    private final FeedbackService service;
    private final VerifiedFeedbackActorResolver actors;
    private final FeedbackConverter converter;

    @PutMapping
    @Operation(
            summary = "내 피드백 등록 또는 수정",
            description = "내 세션의 완료된 ASSISTANT ANSWER 또는 STORE_RESULT 메시지만 평가할 수 있습니다. "
                    + "같은 메시지를 다시 요청하면 기존 평가를 수정합니다. "
                    + "LIKE는 사유 없이, DISLIKE는 사유와 함께 보냅니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "평가 등록 또는 수정 성공"),
            @ApiResponse(responseCode = "400", description = "FEEDBACK400-0: 잘못된 messageId 또는 평가와 사유 조합. "
                    + "COMMON400-0: JSON 형식이나 enum 값 오류. COMMON400-1: 필수값, 길이 또는 경로 변수 형식 오류"),
            @ApiResponse(responseCode = "401", description = "CHAT401-0: 사용할 수 있는 회원 또는 게스트 신원 없음"),
            @ApiResponse(responseCode = "404", description = "FEEDBACK404-0: 메시지가 없거나 내 세션의 메시지가 아님"),
            @ApiResponse(responseCode = "409", description = "FEEDBACK409-0: 완료된 상담 답변 또는 매장 추천이 아님")
    })
    public CustomResponse<FeedbackResponse> save(
            @Parameter(description = "평가할 답변 메시지 ID") @PathVariable long messageId,
            @Valid @RequestBody FeedbackSaveRequest request,
            HttpServletRequest httpRequest) {
        Actor actor = actor(httpRequest);
        checkId(messageId);
        return CustomResponse.onSuccess(
                converter.toResponse(service.save(messageId, actor, converter.toInput(request))));
    }

    @GetMapping
    @Operation(summary = "내 피드백 조회", description = "내 세션의 메시지에 남긴 평가를 조회합니다. 평가가 없으면 result는 null입니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공. 평가가 없으면 result=null"),
            @ApiResponse(responseCode = "400", description = "FEEDBACK400-0: messageId가 0 이하. "
                    + "COMMON400-1: messageId 형식 오류"),
            @ApiResponse(responseCode = "401", description = "CHAT401-0: 사용할 수 있는 회원 또는 게스트 신원 없음"),
            @ApiResponse(responseCode = "404", description = "FEEDBACK404-0: 메시지가 없거나 내 세션의 메시지가 아님")
    })
    public CustomResponse<FeedbackResponse> get(
            @Parameter(description = "조회할 메시지 ID") @PathVariable long messageId, HttpServletRequest request) {
        Actor actor = actor(request);
        checkId(messageId);
        return CustomResponse.onSuccess(
                service.get(messageId, actor).map(converter::toResponse).orElse(null));
    }

    @DeleteMapping
    @Operation(summary = "내 피드백 취소", description = "내 세션의 메시지에 남긴 평가를 삭제합니다. 평가가 없어도 성공합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "취소 성공. 평가가 없으면 그대로 성공"),
            @ApiResponse(responseCode = "400", description = "FEEDBACK400-0: messageId가 0 이하. "
                    + "COMMON400-1: messageId 형식 오류"),
            @ApiResponse(responseCode = "401", description = "CHAT401-0: 사용할 수 있는 회원 또는 게스트 신원 없음"),
            @ApiResponse(responseCode = "404", description = "FEEDBACK404-0: 메시지가 없거나 내 세션의 메시지가 아님")
    })
    public CustomResponse<Void> delete(
            @Parameter(description = "평가를 취소할 메시지 ID") @PathVariable long messageId,
            HttpServletRequest request) {
        Actor actor = actor(request);
        checkId(messageId);
        service.cancel(messageId, actor);
        return CustomResponse.onSuccess(null);
    }

    private Actor actor(HttpServletRequest request) {
        Actor actor = actors.resolve(request);
        if (actor == null) {
            throw new GeneralException(CommonErrorCode.UNAUTHORIZED);
        }
        return actor;
    }

    private void checkId(long id) {
        if (id <= 0) {
            throw new GeneralException(FeedbackErrorCode.INVALID_REQUEST);
        }
    }
}
