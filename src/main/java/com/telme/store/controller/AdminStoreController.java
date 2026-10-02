package com.telme.store.controller;

import com.telme.global.common.CustomResponse;
import com.telme.store.dto.req.AdminStoreSaveRequest;
import com.telme.store.dto.req.AdminStoreSearchRequest;
import com.telme.store.dto.res.AdminStoreDetailResponse;
import com.telme.store.dto.res.AdminStoreListResponse;
import com.telme.store.dto.res.AdminStoreServiceResponse;
import com.telme.store.service.AdminStoreCommandService;
import com.telme.store.service.AdminStoreQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Store", description = "관리자 매장 조회·등록·수정·삭제")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/stores")
public class AdminStoreController {

    private final AdminStoreQueryService adminStoreQueryService;
    private final AdminStoreCommandService adminStoreCommandService;
    
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
    
    @Operation(
            summary = "취급 업무 선택지 조회",
            description = "등록·수정 화면의 업무 체크박스에 쓸 업무 코드와 이름을 id 순으로 반환합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(responseCode = "401", description = "로그인하지 않음"),
        @ApiResponse(responseCode = "403", description = "Admin 권한 없음")
    })
    @GetMapping("/service-types")
    public CustomResponse<List<AdminStoreServiceResponse>> getServiceTypes() {
        return CustomResponse.onSuccess(adminStoreQueryService.getServiceTypes());
    }
    
    @Operation(
            summary = "매장 등록",
            description = "기본 정보·법정동코드·좌표, 요일별 영업시간(월~일 7일), 취급 업무(1개 이상)를 함께 저장합니다. " + "상태는 OPEN으로 시작합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "등록 성공"),
        @ApiResponse(responseCode = "400", description = "COMMON400-1: 필수값·길이·법정동코드 형식·좌표 범위, " 
                + "영업시간 7일 누락(weekComplete), 휴무·시간 불일치(hours[n].timeValid), 업무 중복(serviceCodesUnique)"),
        @ApiResponse(responseCode = "401", description = "로그인하지 않음"),
        @ApiResponse(responseCode = "403", description = "Admin 권한 없음")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomResponse<AdminStoreDetailResponse> createStore(
            @Valid @RequestBody AdminStoreSaveRequest request,
            @AuthenticationPrincipal Long adminId) {
        return CustomResponse.onSuccess(HttpStatus.CREATED, adminStoreCommandService.create(request, adminId));
    }
    
    @Operation(
            summary = "매장 수정",
            description =  "등록과 같은 항목을 받아 전체를 바꿉니다. 영업시간·업무만 바꿔도 수정 시각이 올라갑니다. 상태는 바꾸지 않습니다. "
                    + "조회에서 받은 lockVersion을 함께 보내면 그 사이 다른 관리자가 저장한 경우를 409로 막습니다. "
                    + "폐점 매장은 수정할 수 없습니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "수정 성공"),
        @ApiResponse(responseCode = "400", description = "COMMON400-1: 등록과 같은 입력 오류"),
        @ApiResponse(responseCode = "401", description = "로그인하지 않음"),
        @ApiResponse(responseCode = "403", description = "Admin 권한 없음"),
        @ApiResponse(responseCode = "404", description = "STORE404-0: 매장을 찾을 수 없음"),
        @ApiResponse(responseCode = "409", description = "STORE409-0: 폐점한 매장. STORE409-1: 다른 관리자가 먼저 저장함")
    })
    @PutMapping("/{storeId}")
    public CustomResponse<AdminStoreDetailResponse> updateStore(
            @Parameter(description = "수정할 매장 ID") @PathVariable long storeId,
            @Valid @RequestBody AdminStoreSaveRequest request,
            @AuthenticationPrincipal Long adminId) {
        return CustomResponse.onSuccess(adminStoreCommandService.update(storeId, request, adminId));
    }
    
    @Operation(
            summary = "매장 삭제",
            description = "실제로 지우지 않고 상태를 CLOSED_DOWN(폐점)으로 바꿉니다. 목록 기본 조회에서 빠지고 상태 필터로 다시 볼 수 있습니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "삭제 성공 (이미 폐점이어도 성공)"),
        @ApiResponse(responseCode = "401", description = "로그인하지 않음"),
        @ApiResponse(responseCode = "403", description = "Admin 권한 없음"),
        @ApiResponse(responseCode = "404", description = "STORE404-0: 매장을 찾을 수 없음")
    })
    @DeleteMapping("/{storeId}")
    public CustomResponse<Void> deleteStore(
            @Parameter(description = "삭제(폐점)할 매장 ID") @PathVariable long storeId,
            @AuthenticationPrincipal Long adminId) {
        adminStoreCommandService.delete(storeId, adminId);
        return CustomResponse.onSuccess(null);
    }
}
