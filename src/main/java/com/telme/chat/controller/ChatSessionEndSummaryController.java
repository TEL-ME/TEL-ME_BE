package com.telme.chat.controller;

import com.telme.chat.dto.res.ChatSessionEndSummaryResponse;
import com.telme.chat.service.ChatActorProvider;
import com.telme.chat.service.ChatSessionEndSummaryService;
import com.telme.global.common.CustomResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Validated
@Tag(name = "Chat Session", description = "채팅 세션과 사용자 메시지 API")
@RequestMapping("/api/v1/chat/sessions")
public class ChatSessionEndSummaryController {
    private final ChatActorProvider actors;
    private final ChatSessionEndSummaryService service;

    @Operation(summary = "상담 종료 요약 조회", description = "본인 상담의 저장된 요약과 생성 상태를 조회합니다. "
            + "조회로 생성·재시도·상담 변경을 하지 않습니다. COMPLETED에서만 summary와 generatedAt이 있습니다. "
            + "NOT_REQUESTED, PENDING, GENERATING, EMPTY, FAILED는 요약 본문 없이 안내를 반환합니다.")
    @ApiResponse(responseCode = "200", description = "상태 조회 성공. 준비 중 응답도 200이며 SSE 접수가 아닙니다.")
    @ApiResponse(responseCode = "404", description = "상담이 없거나 본인 상담이 아님")
    @GetMapping("/{sessionId}/summary")
    public ResponseEntity<CustomResponse<ChatSessionEndSummaryResponse>> getSummary(
            HttpServletRequest request, @PathVariable @Positive Long sessionId) {
        var result = service.read(actors.getCurrentActor(request), sessionId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(CustomResponse.onSuccess(result));
    }
}
