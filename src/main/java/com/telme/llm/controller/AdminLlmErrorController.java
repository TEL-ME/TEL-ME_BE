package com.telme.llm.controller;

import com.telme.global.common.CustomResponse;
import com.telme.llm.dto.req.AdminLlmErrorSearchRequest;
import com.telme.llm.dto.res.AdminLlmErrorListResponse;
import com.telme.llm.service.AdminLlmErrorQueryService;
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
public class AdminLlmErrorController {

    private final AdminLlmErrorQueryService adminLlmErrorQueryService;
    
    @Operation(
            summary = "LLM 오류 목록 조회",
            description = "LLM 호출 중 실패한 기록을 최신순으로 반환합니다. 재시도한 호출은 시도마다 한 건씩 나옵니다. "
            + "errorType을 비우면 TIMEOUT·CONNECTION_FAILED·MODEL_ERROR 전체, taskType을 비우면 모든 작업입니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "COMMON400-1: 잘못된 오류 종류·작업 종류·페이지 값"),
            @ApiResponse(responseCode = "401", description = "로그인하지 않음"),
            @ApiResponse(responseCode = "403", description = "Admin 권한 없음")
            })
    @GetMapping("/errors")
    public CustomResponse<AdminLlmErrorListResponse> getErrors(
            @ParameterObject @Valid @ModelAttribute AdminLlmErrorSearchRequest request) {
        return CustomResponse.onSuccess(adminLlmErrorQueryService.getErrors(request));
    }
}
