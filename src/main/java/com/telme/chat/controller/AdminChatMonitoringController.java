package com.telme.chat.controller;

import com.telme.chat.dto.req.AdminChatMonitoringSearchRequest;
import com.telme.chat.dto.res.AdminChatMonitoringDetailResponse;
import com.telme.chat.dto.res.AdminChatMonitoringListResponse;
import com.telme.chat.service.AdminChatMonitoringQueryService;
import com.telme.global.common.CustomResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Chat Monitoring", description = "관리자 AI 처리 흐름 모니터링")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/monitoring/executions")
public class AdminChatMonitoringController {

    private final AdminChatMonitoringQueryService adminChatMonitoringQueryService;

    @Operation(summary = "AI 처리 목록 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "COMMON400-1: status 값 또는 size·beforeId 범위 오류"),
            @ApiResponse(responseCode = "401", description = "로그인하지 않음"),
            @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음")
    })
    @GetMapping
    public CustomResponse<AdminChatMonitoringListResponse> getExecutions(
            @Valid @ParameterObject @ModelAttribute AdminChatMonitoringSearchRequest request) {
        return CustomResponse.onSuccess(adminChatMonitoringQueryService.getExecutions(request));
    }

    @Operation(summary = "AI 처리 상세 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "COMMON400-1: 실행 ID 형식 오류"),
            @ApiResponse(responseCode = "401", description = "로그인하지 않음"),
            @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음"),
            @ApiResponse(responseCode = "404", description = "CHAT404-1: 실행 이력을 찾을 수 없음")
    })
    @GetMapping("/{executionId}")
    public CustomResponse<AdminChatMonitoringDetailResponse> getExecution(
            @Parameter(description = "조회할 실행 ID") @PathVariable long executionId,
            @AuthenticationPrincipal Long adminId) {
        return CustomResponse.onSuccess(adminChatMonitoringQueryService.getExecution(executionId, adminId));
    }
}
