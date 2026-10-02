package com.telme.faq.controller;

import com.telme.faq.dto.req.AdminFaqSaveRequest;
import com.telme.faq.dto.req.AdminFaqSearchRequest;
import com.telme.faq.dto.req.AdminFaqStatusRequest;
import com.telme.faq.dto.res.AdminFaqDetailResponse;
import com.telme.faq.dto.res.AdminFaqListResponse;
import com.telme.faq.service.AdminFaqCommandService;
import com.telme.faq.service.AdminFaqQueryService;
import com.telme.global.common.CustomResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Faq", description = "관리자 FAQ 조회")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/faqs")
public class AdminFaqController {

    private final AdminFaqQueryService adminFaqQueryService;
    private final AdminFaqCommandService adminFaqCommandService;

    @Operation(
            summary = "FAQ 목록 조회",
            description = "검색어·카테고리·상태로 거른 FAQ를 인용 횟수와 함께 반환합니다. "
                    + "카테고리는 10종 코드 중 하나이고, 상태 기본값은 ACTIVE이며 ALL을 주면 숨김·삭제까지 모두 봅니다. "
                    + "정렬은 RECENT(최근 수정 순), CITATION_DESC, CITATION_ASC 중 하나입니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "COMMON400-1: enum 값, 길이 또는 범위 오류"),
            @ApiResponse(responseCode = "401", description = "로그인하지 않음"),
            @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음")
    })
    @GetMapping
    public CustomResponse<AdminFaqListResponse> getFaqs(
            @Valid @ParameterObject @ModelAttribute AdminFaqSearchRequest request) {
        return CustomResponse.onSuccess(adminFaqQueryService.getFaqs(request));
    }

    @Operation(summary = "FAQ 단건 조회", description = "답변 본문과 버전, 내용 해시, 인용 횟수를 함께 반환합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "로그인하지 않음"),
            @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음"),
            @ApiResponse(responseCode = "404", description = "FAQ404-0: FAQ를 찾을 수 없음")
    })
    @GetMapping("/{faqId}")
    public CustomResponse<AdminFaqDetailResponse> getFaq(
            @Parameter(description = "조회할 FAQ ID") @PathVariable long faqId) {
        return CustomResponse.onSuccess(adminFaqQueryService.getFaq(faqId));
    }

    @Operation(
            summary = "FAQ 등록",
            description = "저장하면서 content_hash를 계산하고 임베딩을 만듭니다. "
                    + "질문과 답변이 같은 FAQ가 이미 있으면 409로 막습니다. "
                    + "임베딩 생성에 실패하면 등록도 취소됩니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "등록 성공"),
            @ApiResponse(responseCode = "400", description = "COMMON400-0: enum 값 오류. COMMON400-1: 필수값 또는 길이 오류"),
            @ApiResponse(responseCode = "401", description = "로그인하지 않음"),
            @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음"),
            @ApiResponse(responseCode = "409", description = "FAQ409-0: 질문과 답변이 같은 FAQ가 이미 있음"),
            @ApiResponse(responseCode = "503", description = "FAQ503-0: 임베딩 서버 호출 실패")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomResponse<AdminFaqDetailResponse> createFaq(
            @Valid @RequestBody AdminFaqSaveRequest request,
            @AuthenticationPrincipal Long adminId) {
        return CustomResponse.onSuccess(
                HttpStatus.CREATED, adminFaqCommandService.create(request, adminId));
    }

    @Operation(
            summary = "FAQ 수정",
            description = "질문이나 답변이 바뀐 경우에만 content_hash와 version이 올라갑니다. "
                    + "카테고리만 바꾸면 버전은 그대로이고, 임베딩에 카테고리를 넣는 구성에서만 다시 만듭니다. "
                    + "status를 생략하면 기존 상태를 그대로 둡니다. "
                    + "조회에서 받은 lockVersion을 함께 보내면 그 사이 다른 관리자가 저장한 경우를 409로 막습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "400", description = "COMMON400-0: enum 값 오류. COMMON400-1: 필수값 또는 길이 오류"),
            @ApiResponse(responseCode = "401", description = "로그인하지 않음"),
            @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음"),
            @ApiResponse(responseCode = "404", description = "FAQ404-0: FAQ를 찾을 수 없음"),
            @ApiResponse(responseCode = "409", description = "FAQ409-0: 질문과 답변이 같은 FAQ가 이미 있음. "
                    + "FAQ409-1: 다른 관리자가 먼저 저장함"),
            @ApiResponse(responseCode = "503", description = "FAQ503-0: 임베딩 서버 호출 실패")
    })
    @PutMapping("/{faqId}")
    public CustomResponse<AdminFaqDetailResponse> updateFaq(
            @Parameter(description = "수정할 FAQ ID") @PathVariable long faqId,
            @Valid @RequestBody AdminFaqSaveRequest request,
            @AuthenticationPrincipal Long adminId) {
        return CustomResponse.onSuccess(adminFaqCommandService.update(faqId, request, adminId));
    }

    @Operation(
            summary = "FAQ 상태 변경",
            description = "질문·답변을 보내지 않고 공개(ACTIVE)·숨김(HIDDEN)만 전환합니다. 삭제는 DELETE가 맡습니다. "
                    + "DELETED를 ACTIVE로 되돌리면 복구가 됩니다. 본문이 그대로라 임베딩을 다시 만들지 않아 "
                    + "복구 직후부터 검색에 다시 잡힙니다. "
                    + "조회에서 받은 lockVersion을 함께 보내면 그 사이 다른 관리자가 저장한 경우를 409로 막습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "변경 성공"),
            @ApiResponse(responseCode = "400", description = "COMMON400-0: enum 값 오류. COMMON400-1: status 누락"),
            @ApiResponse(responseCode = "401", description = "로그인하지 않음"),
            @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음"),
            @ApiResponse(responseCode = "404", description = "FAQ404-0: FAQ를 찾을 수 없음"),
            @ApiResponse(responseCode = "409", description = "FAQ409-0: 지운 사이 같은 내용이 등록돼 되살릴 수 없음. "
                    + "FAQ409-1: 다른 관리자가 먼저 저장함")
    })
    @PutMapping("/{faqId}/status")
    public CustomResponse<AdminFaqDetailResponse> changeFaqStatus(
            @Parameter(description = "상태를 바꿀 FAQ ID") @PathVariable long faqId,
            @Valid @RequestBody AdminFaqStatusRequest request,
            @AuthenticationPrincipal Long adminId) {
        return CustomResponse.onSuccess(
                adminFaqCommandService.changeStatus(faqId, request, adminId));
    }

    @Operation(
            summary = "FAQ 삭제",
            description = "실제로 지우지 않고 상태를 DELETED로 바꿉니다. 검색에서는 바로 빠지고 상태 필터로 다시 볼 수 있습니다. "
                    + "조회에서 받은 lockVersion을 함께 보내면 그 사이 다른 관리자가 저장한 경우를 409로 막습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "삭제 성공"),
            @ApiResponse(responseCode = "401", description = "로그인하지 않음"),
            @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음"),
            @ApiResponse(responseCode = "404", description = "FAQ404-0: FAQ를 찾을 수 없음"),
            @ApiResponse(responseCode = "409", description = "FAQ409-1: 다른 관리자가 먼저 저장함")
    })
    @DeleteMapping("/{faqId}")
    public CustomResponse<Void> deleteFaq(
            @Parameter(description = "삭제할 FAQ ID") @PathVariable long faqId,
            @Parameter(description = "조회에서 받은 lockVersion. 생략하면 검사하지 않음")
            @RequestParam(required = false) Integer lockVersion,
            @AuthenticationPrincipal Long adminId) {
        adminFaqCommandService.delete(faqId, adminId, lockVersion);
        return CustomResponse.onSuccess(null);
    }

    @Operation(
            summary = "FAQ 영구 삭제",
            description = "행을 실제로 지웁니다. 되돌릴 수 없어 이미 삭제 처리된(DELETED) FAQ만 받습니다. "
                    + "답변 근거로 쓰인 적이 있으면 과거 답변의 근거가 끊겨 409로 막습니다. "
                    + "임베딩은 외래키 설정에 따라 함께 사라집니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "영구 삭제 성공"),
            @ApiResponse(responseCode = "401", description = "로그인하지 않음"),
            @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음"),
            @ApiResponse(responseCode = "404", description = "FAQ404-0: FAQ를 찾을 수 없음"),
            @ApiResponse(responseCode = "409", description = "FAQ409-2: 삭제 처리된 FAQ가 아님. "
                    + "FAQ409-3: 답변 근거로 사용된 적이 있음")
    })
    @DeleteMapping("/{faqId}/permanent")
    public CustomResponse<Void> purgeFaq(
            @Parameter(description = "영구 삭제할 FAQ ID") @PathVariable long faqId,
            @AuthenticationPrincipal Long adminId) {
        adminFaqCommandService.purge(faqId, adminId);
        return CustomResponse.onSuccess(null);
    }
}
