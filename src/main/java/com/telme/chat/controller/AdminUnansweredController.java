package com.telme.chat.controller;

import com.telme.chat.dto.req.AdminUnansweredSearchRequest;
import com.telme.chat.dto.res.AdminUnansweredDetailResponse;
import com.telme.chat.dto.res.AdminUnansweredListResponse;
import com.telme.chat.service.AdminUnansweredQueryService;
import com.telme.global.common.CustomResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Unanswered", description = "관리자 답 못 한 질문 조회")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/unanswered")
public class AdminUnansweredController {

    private final AdminUnansweredQueryService adminUnansweredQueryService;

    @Operation(
            summary = "답 못 한 질문 목록 조회",
            description = "챗봇이 근거를 못 찾았거나 답변을 끝내지 못한 경우를 최신순으로 반환합니다. "
                    + "유형은 NO_EVIDENCE·OUT_OF_SCOPE·FAILED·TIMEOUT 중 하나이고, 생략하면 네 가지를 모두 봅니다. "
                    + "from·to는 답변 시각 기준이고 to는 그 시각 직전까지입니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "COMMON400-1: enum 값 또는 범위 오류. "
                    + "CHAT400-1: 기간의 시작이 끝보다 늦음"),
            @ApiResponse(responseCode = "401", description = "로그인하지 않음"),
            @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음")
    })
    @GetMapping
    public CustomResponse<AdminUnansweredListResponse> getUnanswered(
            @Valid @ParameterObject @ModelAttribute AdminUnansweredSearchRequest request) {
        return CustomResponse.onSuccess(adminUnansweredQueryService.getUnanswered(request));
    }

    @Operation(
            summary = "답 못 한 질문 단건 조회",
            description = "사용자가 물어본 질문, 챗봇이 내보낸 응답, 답을 만들 때 뽑힌 FAQ와 점수를 함께 반환합니다. "
                    + "근거가 비어 있으면 쓸 FAQ가 없었다는 뜻이고, 있는데 점수가 낮으면 질문과 FAQ 표현이 어긋난 것입니다. "
                    + "답 못 한 질문이 아닌 메시지는 404입니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "로그인하지 않음"),
            @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음"),
            @ApiResponse(responseCode = "404", description = "CHAT404-2: 답 못 한 질문을 찾을 수 없음")
    })
    @GetMapping("/{messageId}")
    public CustomResponse<AdminUnansweredDetailResponse> getUnanswered(
            @Parameter(description = "조회할 답변 메시지 ID") @PathVariable long messageId) {
        return CustomResponse.onSuccess(adminUnansweredQueryService.getUnanswered(messageId));
    }
}
