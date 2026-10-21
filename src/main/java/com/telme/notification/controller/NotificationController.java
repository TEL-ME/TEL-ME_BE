package com.telme.notification.controller;

import com.telme.chat.service.ChatActor;
import com.telme.chat.service.ChatActorProvider;
import com.telme.global.common.CustomResponse;
import com.telme.notification.converter.NotificationConverter;
import com.telme.notification.dto.req.NotificationCreateRequest;
import com.telme.notification.dto.res.NotificationListResponse;
import com.telme.notification.dto.res.NotificationReadResponse;
import com.telme.notification.dto.res.NotificationResponse;
import com.telme.notification.dto.res.NotificationUnreadCountResponse;
import com.telme.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 게스트도 써야 해서 비로그인 허용 경로(/api/v1/chat/**) 아래에 둔다
@Tag(name = "Notification", description = "새 답변 알림 저장, 목록, 안 읽은 개수, 채팅방 단위 읽음 처리")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/chat/notifications")
public class NotificationController {

    private final ChatActorProvider chatActorProvider;
    private final NotificationService notificationService;
    private final NotificationConverter notificationConverter;

    @Operation(
            summary = "알림 저장",
            description = "다른 화면에 있거나 탭이 뒤에 있을 때 알림이 울리면 프론트가 저장합니다. "
                    + "ANSWER_ARRIVED는 완료된 챗봇 답변·되묻기·매장 결과, ANSWER_FAILED는 실패·시간 초과 답변만 "
                    + "저장할 수 있습니다(취소된 답변 제외). 같은 메시지로 다시 요청하면 기존 알림을 돌려줍니다. "
                    + "답변 처리는 끝났지만 종료 결과를 전달하지 못한 실행은 서버가 직접 저장합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "저장 성공 또는 이미 저장된 알림"),
            @ApiResponse(responseCode = "400", description = "COMMON400-0: JSON 형식이나 type 값 오류. "
                    + "COMMON400-1: 필수값 누락, ID가 0 이하"),
            @ApiResponse(responseCode = "401", description = "CHAT401-0: 사용할 수 있는 회원 또는 게스트 신원 없음"),
            @ApiResponse(responseCode = "404", description = "NOTIFICATION404-0: 메시지가 없거나, 내 채팅방 메시지가 아니거나, "
                    + "알림 종류에 맞는 대상이 아님")
    })
    @PostMapping
    public CustomResponse<NotificationResponse> save(
            HttpServletRequest servletRequest,
            @Valid @RequestBody NotificationCreateRequest request
    ) {
        ChatActor actor = chatActorProvider.getCurrentActor(servletRequest);
        return CustomResponse.onSuccess(notificationConverter.toResponse(notificationService.save(
                actor, request.type(), request.sessionId(), request.messageId())));
    }

    @Operation(
            summary = "내 알림 목록",
            description = "최신순입니다. hasNext가 true이면 nextCursor를 다음 요청의 cursor에 넣습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "COMMON400-1: cursor가 0 이하이거나 size가 1~50 밖"),
            @ApiResponse(responseCode = "401", description = "CHAT401-0: 사용할 수 있는 회원 또는 게스트 신원 없음")
    })
    @GetMapping
    public CustomResponse<NotificationListResponse> getNotifications(
            HttpServletRequest servletRequest,
            @Parameter(description = "다음 페이지를 조회할 때 이전 응답의 nextCursor 값. 첫 조회에서는 생략합니다.")
            @RequestParam(required = false)
            @Positive(message = "알림 커서는 1 이상이어야 합니다.")
            Long cursor,
            @Parameter(description = "조회할 알림 수 (1~50)")
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "조회 개수는 1 이상이어야 합니다.")
            @Max(value = 50, message = "조회 개수는 50 이하여야 합니다.")
            int size
    ) {
        ChatActor actor = chatActorProvider.getCurrentActor(servletRequest);
        return CustomResponse.onSuccess(
                notificationConverter.toListResponse(notificationService.getPage(actor, cursor, size)));
    }

    @Operation(summary = "안 읽은 알림 개수", description = "알림 아이콘의 점 표시에 씁니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "CHAT401-0: 사용할 수 있는 회원 또는 게스트 신원 없음")
    })
    @GetMapping("/unread-count")
    public CustomResponse<NotificationUnreadCountResponse> getUnreadCount(HttpServletRequest servletRequest) {
        ChatActor actor = chatActorProvider.getCurrentActor(servletRequest);
        return CustomResponse.onSuccess(
                notificationConverter.toUnreadCountResponse(notificationService.countUnread(actor)));
    }

    @Operation(
            summary = "채팅방 알림 읽음 처리",
            description = "채팅방 화면에 들어가면 부릅니다. 그 채팅방의 안 읽은 알림을 모두 읽음 처리하고 처리한 개수를 돌려줍니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "처리 성공. 이미 다 읽었으면 readCount=0"),
            @ApiResponse(responseCode = "400", description = "COMMON400-1: sessionId 누락 또는 0 이하"),
            @ApiResponse(responseCode = "401", description = "CHAT401-0: 사용할 수 있는 회원 또는 게스트 신원 없음"),
            @ApiResponse(responseCode = "404", description = "CHAT404-0: 채팅방이 없거나 내 채팅방이 아님")
    })
    @PatchMapping("/read")
    public CustomResponse<NotificationReadResponse> readSession(
            HttpServletRequest servletRequest,
            // 필수 @RequestParam 누락은 500이 되어 @NotNull로 검증한다
            @Parameter(description = "들어간 채팅방 ID", required = true)
            @RequestParam(required = false)
            @NotNull(message = "채팅방 ID는 필수입니다.")
            @Positive(message = "채팅방 ID는 1 이상이어야 합니다.")
            Long sessionId
    ) {
        ChatActor actor = chatActorProvider.getCurrentActor(servletRequest);
        return CustomResponse.onSuccess(
                notificationConverter.toReadResponse(notificationService.readSession(actor, sessionId)));
    }
}
