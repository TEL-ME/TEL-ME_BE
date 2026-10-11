package com.telme.chat.controller;

import com.telme.chat.dto.res.ChatInputGuardStatusResponse;
import com.telme.chat.guard.ChatInputGuardStatusService;
import com.telme.chat.service.ChatActor;
import com.telme.chat.service.ChatActorProvider;
import com.telme.global.common.CustomResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.SchemaProperty;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Chat Input Guard", description = "현재 사용자 채팅 입력 제한 조회")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/chat/input-guard")
public class ChatInputGuardController {

    private final ChatActorProvider chatActorProvider;
    private final ChatInputGuardStatusService statusService;

    @Operation(
            summary = "내 채팅 입력 제한 상태 조회",
            description = "현재 인증 세션의 회원·게스트만 조회합니다. 사용자 ID나 채팅 세션 ID를 받지 않습니다. "
                    + "조회로 감지 횟수·제한·상담 상태를 변경하지 않으며 실제 전송 시 서버 입력 검사는 유지됩니다."
    )
    @ApiResponse(
            responseCode = "200",
            description = "제한 여부와 남은 시간. 기록이 없거나 만료됐다면 restricted=false, 남은 시간 0초입니다.",
            headers = @Header(
                    name = "Retry-After",
                    description = "제한 중일 때 남은 시간과 동일한 초 단위 값",
                    schema = @Schema(type = "integer", format = "int64")
            ),
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(type = "object"),
                    schemaProperties = {
                            @SchemaProperty(name = "isSuccess", schema = @Schema(type = "boolean")),
                            @SchemaProperty(name = "code", schema = @Schema(type = "string")),
                            @SchemaProperty(name = "message", schema = @Schema(type = "string")),
                            @SchemaProperty(name = "result",
                                    schema = @Schema(implementation = ChatInputGuardStatusResponse.class))
                    },
                    examples = {
                            @ExampleObject(
                                    name = "restricted",
                                    summary = "일시 제한 중",
                                    value = """
                                            {
                                              "isSuccess": true,
                                              "code": "200",
                                              "message": "OK",
                                              "result": {
                                                "restricted": true,
                                                "restrictionUntil": "2026-10-08T00:01:00Z",
                                                "retryAfterSeconds": 40,
                                                "serverTime": "2026-10-08T00:00:20Z"
                                              }
                                            }
                                            """
                            ),
                            @ExampleObject(
                                    name = "unrestricted",
                                    summary = "기록 없음 또는 제한 만료",
                                    value = """
                                            {
                                              "isSuccess": true,
                                              "code": "200",
                                              "message": "OK",
                                              "result": {
                                                "restricted": false,
                                                "restrictionUntil": null,
                                                "retryAfterSeconds": 0,
                                                "serverTime": "2026-10-08T00:01:00Z"
                                              }
                                            }
                                            """
                            )
                    }
            )
    )
    @GetMapping("/status")
    public ResponseEntity<CustomResponse<ChatInputGuardStatusResponse>> getStatus(
            HttpServletRequest servletRequest
    ) {
        ChatActor actor = chatActorProvider.getCurrentActor(servletRequest);
        ChatInputGuardStatusResponse response = statusService.getStatus(actor);
        var builder = ResponseEntity.ok().cacheControl(CacheControl.noStore());
        if (response.restricted()) {
            builder.header(HttpHeaders.RETRY_AFTER, Long.toString(response.retryAfterSeconds()));
        }
        return builder.body(CustomResponse.onSuccess(response));
    }
}
