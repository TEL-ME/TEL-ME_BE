package com.telme.faq.controller;

import com.telme.faq.dto.req.AdminSearchScoreRequest;
import com.telme.faq.dto.res.AdminSearchScoreResponse;
import com.telme.faq.service.AdminSearchScoreQueryService;
import com.telme.global.common.CustomResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin System", description = "관리자 운영 상태")
@RestController
@RequestMapping("/api/v1/admin/system")
@RequiredArgsConstructor
public class AdminSearchScoreController {

    private final AdminSearchScoreQueryService adminSearchScoreQueryService;
    
    @Operation(
            summary = "검색 점수 분포 조회",
            description = "FAQ 검색마다 남긴 질문+답변 벡터(Q_A) 1위 점수의 분포(0~1, 0.05 간격 20칸)와 "
                    + "현재 임계값(threshold), 임계값 이상 검색 수, 실제로 근거를 찾은 검색 수를 반환합니다. "
                    + "기간을 비우면 전체입니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(responseCode = "400", description = "COMMON400-1: 시각 형식 오류, 시작 시각이 끝 시각보다 늦음(periodValid)"),
        @ApiResponse(responseCode = "401", description = "로그인하지 않음"),
        @ApiResponse(responseCode = "403", description = "Admin 권한 없음")
    })
    
    @GetMapping("/search-scores")
    public CustomResponse<AdminSearchScoreResponse> getScores(
            @ParameterObject @Valid @ModelAttribute AdminSearchScoreRequest request) {
        return CustomResponse.onSuccess(adminSearchScoreQueryService.getScores(request));
    }
}
