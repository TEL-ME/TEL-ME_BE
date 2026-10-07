package com.telme.chat.controller;

import com.telme.chat.dto.req.ChatMessageSendRequest;
import com.telme.chat.dto.req.ChatSessionCreateRequest;
import com.telme.chat.dto.req.ChatSessionTitleUpdateRequest;
import com.telme.chat.dto.res.ChatMessageHistoryResponse;
import com.telme.chat.dto.res.ChatMessageSendResponse;
import com.telme.chat.dto.res.ChatSessionCreateResponse;
import com.telme.chat.dto.res.ChatSessionListResponse;
import com.telme.chat.dto.res.ChatSessionUpdateResponse;
import com.telme.chat.service.ChatActor;
import com.telme.chat.service.ChatActorProvider;
import com.telme.chat.service.ChatSessionService;
import com.telme.global.common.CustomResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.SchemaProperty;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Chat Session", description = "채팅 세션과 사용자 메시지 API")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/chat/sessions")
public class ChatSessionController {

    private final ChatActorProvider chatActorProvider;
    private final ChatSessionService chatSessionService;

    @Operation(summary = "채팅 세션 생성")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomResponse<ChatSessionCreateResponse> createSession(
            HttpServletRequest servletRequest,
            @Valid @RequestBody ChatSessionCreateRequest request
    ) {
        ChatActor actor = chatActorProvider.getCurrentActor(servletRequest);
        ChatSessionCreateResponse response = chatSessionService.createSession(actor, request);
        return CustomResponse.onSuccess(HttpStatus.CREATED, response);
    }

    @Operation(summary = "내 채팅 세션 목록 조회")
    @GetMapping
    public CustomResponse<ChatSessionListResponse> getSessions(
            HttpServletRequest servletRequest,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "조회 개수는 1 이상이어야 합니다.")
            @Max(value = 50, message = "조회 개수는 50 이하여야 합니다.")
            int size
    ) {
        ChatActor actor = chatActorProvider.getCurrentActor(servletRequest);
        return CustomResponse.onSuccess(chatSessionService.getSessions(actor, cursor, size));
    }

    @Operation(
            summary = "대화 이력 조회",
            description = "최신 메시지 구간을 조회하며 응답은 sequenceNo 오름차순입니다. "
                    + "hasOlderMessages가 true이면 nextBeforeSequenceNo를 다음 요청에 넣어 더 과거 이력을 조회합니다."
    )
    @GetMapping("/{sessionId}/messages")
    public CustomResponse<ChatMessageHistoryResponse> getMessages(
            HttpServletRequest servletRequest,
            @PathVariable Long sessionId,
            @Parameter(
                    description = "더 과거 이력을 조회할 때 이전 응답의 nextBeforeSequenceNo 값을 사용합니다. "
                            + "첫 조회에서는 생략합니다."
            )
            @RequestParam(required = false)
            @Positive(message = "메시지 커서는 1 이상이어야 합니다.")
            Integer beforeSequenceNo,
            @Parameter(description = "조회할 메시지 수 (1~50)")
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "조회 개수는 1 이상이어야 합니다.")
            @Max(value = 50, message = "조회 개수는 50 이하여야 합니다.")
            int size
    ) {
        ChatActor actor = chatActorProvider.getCurrentActor(servletRequest);
        return CustomResponse.onSuccess(
                chatSessionService.getMessages(actor, sessionId, beforeSequenceNo, size)
        );
    }

    @Operation(summary = "채팅 세션 제목 변경")
    @PatchMapping("/{sessionId}/title")
    public CustomResponse<ChatSessionUpdateResponse> updateTitle(
            HttpServletRequest servletRequest,
            @PathVariable Long sessionId,
            @Valid @RequestBody ChatSessionTitleUpdateRequest request
    ) {
        ChatActor actor = chatActorProvider.getCurrentActor(servletRequest);
        return CustomResponse.onSuccess(chatSessionService.updateTitle(actor, sessionId, request));
    }

    @Operation(summary = "채팅 세션 종료")
    @PatchMapping("/{sessionId}/close")
    public CustomResponse<ChatSessionUpdateResponse> closeSession(
            HttpServletRequest servletRequest,
            @PathVariable Long sessionId
    ) {
        ChatActor actor = chatActorProvider.getCurrentActor(servletRequest);
        return CustomResponse.onSuccess(chatSessionService.closeSession(actor, sessionId));
    }

    @Operation(
            summary = "사용자 메시지 전송",
            description = "inputGuard가 있어도 MASKED는 정상 접수입니다. 실행 ID 유무로 생성 접수 여부를 구분합니다.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "정상 접수 및 마스킹 후 정상 접수. executionId와 executionStatus가 있으며 기존 SSE 흐름을 이어갑니다.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(type = "object"),
                            schemaProperties = {
                                    @SchemaProperty(name = "isSuccess", schema = @Schema(type = "boolean")),
                                    @SchemaProperty(name = "code", schema = @Schema(type = "string")),
                                    @SchemaProperty(name = "message", schema = @Schema(type = "string")),
                                    @SchemaProperty(name = "result",
                                            schema = @Schema(implementation = ChatMessageSendResponse.class))
                            },
                            examples = {
                                    @ExampleObject(
                                            name = "normal",
                                            summary = "정상 접수",
                                            value = """
                                                    {
                                                      "isSuccess": true,
                                                      "code": "201",
                                                      "message": "Created",
                                                      "result": {
                                                        "sessionId": 123,
                                                        "messageId": 501,
                                                        "sequenceNo": 1,
                                                        "executionId": 901,
                                                        "executionStatus": "RUNNING",
                                                        "createdAt": "2026-10-08T00:00:00Z"
                                                      }
                                                    }
                                                    """),
                                    @ExampleObject(
                                            name = "masked",
                                            summary = "마스킹 후 정상 접수",
                                            value = """
                                                    {
                                                      "isSuccess": true,
                                                      "code": "201",
                                                      "message": "Created",
                                                      "result": {
                                                        "sessionId": 123,
                                                        "messageId": 501,
                                                        "sequenceNo": 1,
                                                        "executionId": 901,
                                                        "executionStatus": "RUNNING",
                                                        "createdAt": "2026-10-08T00:00:00Z",
                                                        "inputGuard": {
                                                          "action": "MASKED",
                                                          "message": "민감정보를 가리고 문의를 처리합니다.",
                                                          "violationCount": 0,
                                                          "retryAfterSeconds": 0,
                                                          "restrictionStartedAt": null,
                                                          "restrictionUntil": null,
                                                          "detections": [
                                                            {
                                                              "reason": "SENSITIVE_INFORMATION",
                                                              "ruleId": "PII_PAYMENT_CARD"
                                                            }
                                                          ]
                                                        }
                                                      }
                                                    }
                                                    """)
                            })),
            @ApiResponse(
                    responseCode = "200",
                    description = "경고·일시 제한·재입력 안내. executionId와 executionStatus는 JSON에서 생략되며 SSE·생성 재시도를 시작하지 않습니다.",
                    headers = @Header(
                            name = "Retry-After",
                            description = "남은 제한 시간이 양수일 때 초 단위로 반환합니다.",
                            schema = @Schema(type = "integer", format = "int64")),
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(type = "object"),
                            schemaProperties = {
                                    @SchemaProperty(name = "isSuccess", schema = @Schema(type = "boolean")),
                                    @SchemaProperty(name = "code", schema = @Schema(type = "string")),
                                    @SchemaProperty(name = "message", schema = @Schema(type = "string")),
                                    @SchemaProperty(name = "result",
                                            schema = @Schema(implementation = ChatMessageSendResponse.class))
                            },
                            examples = {
                                    @ExampleObject(
                                            name = "warned",
                                            summary = "욕설 경고",
                                            value = """
                                                    {
                                                      "isSuccess": true,
                                                      "code": "200",
                                                      "message": "OK",
                                                      "result": {
                                                        "sessionId": 123,
                                                        "messageId": 502,
                                                        "sequenceNo": 2,
                                                        "createdAt": "2026-10-08T00:00:00Z",
                                                        "inputGuard": {
                                                          "action": "WARNED",
                                                                "message": "원활한 상담을 위해 욕설을 제외하고 질문해 주세요. \
                                                    최근 집계 기간 내 3회 감지되면 일시 제한됩니다.",
                                                          "violationCount": 1,
                                                          "retryAfterSeconds": 0,
                                                          "restrictionStartedAt": null,
                                                          "restrictionUntil": null,
                                                          "detections": [
                                                            {
                                                              "reason": "INITIAL_PROFANITY",
                                                              "ruleId": "INITIAL_SB"
                                                            }
                                                          ]
                                                        }
                                                      }
                                                    }
                                                    """),
                                    @ExampleObject(
                                            name = "restricted",
                                            summary = "일시 제한",
                                            value = """
                                                    {
                                                      "isSuccess": true,
                                                      "code": "200",
                                                      "message": "OK",
                                                      "result": {
                                                        "sessionId": 123,
                                                        "messageId": 503,
                                                        "sequenceNo": 3,
                                                        "createdAt": "2026-10-08T00:00:00Z",
                                                        "inputGuard": {
                                                          "action": "RESTRICTED",
                                                          "message": "반복된 욕설로 채팅이 일시 제한되었습니다.",
                                                          "violationCount": 3,
                                                          "retryAfterSeconds": 60,
                                                          "restrictionStartedAt": "2026-10-08T00:00:00Z",
                                                          "restrictionUntil": "2026-10-08T00:01:00Z",
                                                          "detections": [
                                                            {
                                                              "reason": "INITIAL_PROFANITY",
                                                              "ruleId": "INITIAL_SB"
                                                            }
                                                          ]
                                                        }
                                                      }
                                                    }
                                                    """),
                                    @ExampleObject(
                                            name = "rewriteRequired",
                                            summary = "재입력 안내",
                                            value = """
                                                    {
                                                      "isSuccess": true,
                                                      "code": "200",
                                                      "message": "OK",
                                                      "result": {
                                                        "sessionId": 123,
                                                        "messageId": 504,
                                                        "sequenceNo": 4,
                                                        "createdAt": "2026-10-08T00:00:00Z",
                                                        "inputGuard": {
                                                          "action": "REWRITE_REQUIRED",
                                                          "message": "민감정보를 제외한 문의 내용을 입력해 주세요.",
                                                          "violationCount": 0,
                                                          "retryAfterSeconds": 0,
                                                          "restrictionStartedAt": null,
                                                          "restrictionUntil": null,
                                                          "detections": [
                                                            {
                                                              "reason": "SENSITIVE_INFORMATION",
                                                              "ruleId": "PII_PAYMENT_CARD"
                                                            }
                                                          ]
                                                        }
                                                      }
                                                    }
                                                    """)
                            }))
    })
    @PostMapping("/{sessionId}/messages")
    public ResponseEntity<CustomResponse<ChatMessageSendResponse>> sendMessage(
            HttpServletRequest servletRequest,
            @PathVariable Long sessionId,
            @Valid @RequestBody ChatMessageSendRequest request
    ) {
        ChatActor actor = chatActorProvider.getCurrentActor(servletRequest);
        ChatMessageSendResponse response = chatSessionService.sendMessage(actor, sessionId, request);
        HttpStatus status = response.accepted() ? HttpStatus.CREATED : HttpStatus.OK;
        var result = ResponseEntity.status(status);
        if (response.inputGuard() != null && response.inputGuard().retryAfterSeconds() > 0) {
            result.header("Retry-After", Long.toString(response.inputGuard().retryAfterSeconds()));
        }
        return result.body(CustomResponse.onSuccess(status, response));
    }
}
