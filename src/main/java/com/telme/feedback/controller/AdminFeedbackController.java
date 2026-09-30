package com.telme.feedback.controller;

import com.telme.feedback.dto.req.AdminFeedbackSearchRequest;
import com.telme.feedback.dto.res.AdminFeedbackListResponse;
import com.telme.feedback.service.AdminFeedbackQueryService;
import com.telme.global.common.CustomResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Feedback", description = "관리자 싫어요 피드백 조회")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/feedbacks")
// 사용자 쪽 피드백 API와 같은 설정으로 켜고 끈다. 기능을 끄면 관리자 화면도 함께 내린다
@ConditionalOnProperty(name = "telme.feedback.enabled", havingValue = "true")
public class AdminFeedbackController {

    private final AdminFeedbackQueryService adminFeedbackQueryService;

    @Operation(
            summary = "싫어요 목록 조회",
            description = "사용자가 싫어요를 남긴 답변을 최신순으로 반환합니다. "
                    + "사유는 WRONG_INFO·NOT_RELATED·HARD_TO_READ 중 하나이고, 생략하면 전체입니다. "
                    + "handled는 UNHANDLED(기본)·HANDLED·ALL이며, from·to는 남긴 시각 기준이고 to는 그 시각 직전까지입니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "COMMON400-1: enum 값 또는 범위 오류. "
                    + "FEEDBACK400-0: 기간의 시작이 끝보다 늦음"),
            @ApiResponse(responseCode = "401", description = "로그인하지 않음"),
            @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음")
    })
    @GetMapping
    public CustomResponse<AdminFeedbackListResponse> getDislikes(
            @Valid @ParameterObject @ModelAttribute AdminFeedbackSearchRequest request) {
        return CustomResponse.onSuccess(adminFeedbackQueryService.getDislikes(request));
    }
}
