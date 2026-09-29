package com.telme.store.controller;

import com.telme.global.common.CustomResponse;
import com.telme.store.dto.req.AdminStoreSearchRequest;
import com.telme.store.dto.res.AdminStoreDetailResponse;
import com.telme.store.dto.res.AdminStoreListResponse;
import com.telme.store.service.AdminStoreQueryService;
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

@Tag(name = "Admin Store", description = "관리자 매장 조회")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/stores")
public class AdminStoreController {

    private final AdminStoreQueryService adminStoreQueryService;
    
    @Operation(
            summary = "매장 목록 조회",
            description = "검색어(매장명·주소)와 상태로 거른 매장을 최근 수정 순으로 반환합니다. "
                    + "상태 기본값은 OPEN이며 CLOSED_DOWN은 삭제(폐점)한 매장, ALL은 전체입니다. "
                    + "검색어의 %와 _는 글자 그대로 찾습니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(responseCode = "400", description = "COMMON400-1: 상태 값, 검색어 길이, page·size 범위 오류"),
        @ApiResponse(responseCode = "401", description = "로그인 하지 않음"),
        @ApiResponse(responseCode = "403", description = "Admin 권한 없음")
    })
    @GetMapping
    public CustomResponse<AdminStoreListResponse> getStores(
            @Valid @ParameterObject @ModelAttribute AdminStoreSearchRequest request) {
        return CustomResponse.onSuccess(adminStoreQueryService.getStores(request));
    }
    
    @Operation(
            summary = "매장 상세 조회",
            description = "기본 정보·법정동코드·좌표, 월요일부터의 요일별 영업시간, 취급 업무를 반환합니다. 폐점 매장도 조회할 수 있습니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(responseCode = "400", description = "COMMON400-1: 매장 ID 형식 오류"),
        @ApiResponse(responseCode = "401", description = "로그인하지 않음"),
        @ApiResponse(responseCode = "403", description = "Admin 권한 없음"),
        @ApiResponse(responseCode = "404", description = "STORE404-0: 매장을 찾을 수 없음")
    })
    @GetMapping("/{storeId}")
    public CustomResponse<AdminStoreDetailResponse> getStore(
            @Parameter(description = "조회할 매장 ID") @PathVariable long storeId) {
        return CustomResponse.onSuccess(adminStoreQueryService.getStore(storeId));
    }
}
