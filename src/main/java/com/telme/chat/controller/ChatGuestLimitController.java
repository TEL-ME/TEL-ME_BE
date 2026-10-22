package com.telme.chat.controller;

import com.telme.chat.dto.res.ChatGuestLimitResponse;
import com.telme.chat.service.ChatActor;
import com.telme.chat.service.ChatActorProvider;
import com.telme.chat.service.GuestDailyChatLimit;
import com.telme.global.common.CustomResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Chat Guest Limit", description = "게스트 하루 질문 수 조회")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/chat/guest-limit")
public class ChatGuestLimitController {

    private final ChatActorProvider chatActorProvider;
    private final GuestDailyChatLimit guestDailyChatLimit;

    @Operation(
            summary = "오늘 남은 게스트 질문 수 조회",
            description = "현재 세션의 게스트가 오늘(한국 시간 0시 기준) 보낼 수 있는 남은 질문 수입니다. "
                    + "질문을 보낸 뒤의 남은 수는 메시지 전송 응답의 guestQuestionsRemaining으로도 받습니다. "
                    + "회원이거나 제한을 끈 환경이면 limited=false입니다."
    )
    @ApiResponse(
            responseCode = "200",
            description = "남은 질문 수와 다시 차는 시각",
            content = @Content(
                    mediaType = "application/json",
                    examples = {
                            @ExampleObject(
                                    name = "guest",
                                    summary = "게스트",
                                    value = """
                                            {
                                              "isSuccess": true,
                                              "code": "200",
                                              "message": "OK",
                                              "result": {
                                                "limited": true,
                                                "dailyLimit": 10,
                                                "remaining": 3,
                                                "resetsAt": "2026-10-22T15:00:00Z"
                                              }
                                            }
                                            """),
                            @ExampleObject(
                                    name = "member",
                                    summary = "회원",
                                    value = """
                                            {
                                              "isSuccess": true,
                                              "code": "200",
                                              "message": "OK",
                                              "result": {
                                                "limited": false,
                                                "dailyLimit": null,
                                                "remaining": null,
                                                "resetsAt": null
                                              }
                                            }
                                            """)
                    }
            )
    )
    @GetMapping
    public ResponseEntity<CustomResponse<ChatGuestLimitResponse>> getStatus(HttpServletRequest servletRequest) {
        ChatActor actor = chatActorProvider.getCurrentActor(servletRequest);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(CustomResponse.onSuccess(guestDailyChatLimit.status(actor)));
    }
}
