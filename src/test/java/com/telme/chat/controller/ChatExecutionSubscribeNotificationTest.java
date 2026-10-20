package com.telme.chat.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telme.chat.config.ChatExecutionProperties;
import com.telme.chat.entity.ChatExecution;
import com.telme.chat.service.ChatActor;
import com.telme.chat.service.ChatActorProvider;
import com.telme.chat.service.ChatEmitterRegistry;
import com.telme.chat.service.ChatExecutionState;
import com.telme.chat.service.ChatExecutionTraceService;
import com.telme.chat.service.ChatSessionService;
import com.telme.notification.service.NotificationService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

// 늦게 구독한 화면에 결과를 전했을 때만 서버가 남긴 알림을 지우는지 확인한다(전송 실패 시 알림 유지)
class ChatExecutionSubscribeNotificationTest {

    private static final long SESSION_ID = 1L;
    private static final long EXECUTION_ID = 2L;

    private final ChatActor actor = new ChatActor(10L, null);
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final ChatActorProvider actors = mock(ChatActorProvider.class);
    private final ChatSessionService sessions = mock(ChatSessionService.class);
    private final ChatEmitterRegistry emitters = mock(ChatEmitterRegistry.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final ChatExecutionController controller = new ChatExecutionController(
            actors, sessions, emitters, new ChatExecutionProperties(Duration.ofMinutes(5)),
            mock(ChatExecutionTraceService.class),
            new StaticListableBeanFactory(Map.of("notificationService", notifications))
                    .getBeanProvider(NotificationService.class));

    @Test
    @DisplayName("이미 끝난 실행을 구독했는데 결과 전송에 실패하면 서버 알림을 지우지 않는다")
    void 즉시_종료_전송_실패면_알림_유지() {
        when(actors.getCurrentActor(request)).thenReturn(actor);
        when(sessions.getExecution(actor, EXECUTION_ID)).thenReturn(state(ChatExecution.Status.COMPLETED));
        when(emitters.sendTerminalNow(any(), eq("complete"), any())).thenReturn(false);

        controller.subscribe(request, SESSION_ID, EXECUTION_ID);

        verify(notifications, never()).dismissDelivered(anyLong());
    }

    @Test
    @DisplayName("이미 끝난 실행의 결과를 전했으면 서버 알림을 지운다")
    void 즉시_종료_전송_성공이면_알림_삭제() {
        when(actors.getCurrentActor(request)).thenReturn(actor);
        when(sessions.getExecution(actor, EXECUTION_ID)).thenReturn(state(ChatExecution.Status.FAILED));
        when(emitters.sendTerminalNow(any(), eq("error"), any())).thenReturn(true);

        controller.subscribe(request, SESSION_ID, EXECUTION_ID);

        verify(notifications).dismissDelivered(EXECUTION_ID);
    }

    @Test
    @DisplayName("구독 등록 직후 끝난 실행은 여기서 결과를 전했을 때만 알림을 지운다")
    void 등록_후_재확인_경로() {
        when(actors.getCurrentActor(request)).thenReturn(actor);
        when(sessions.getExecution(actor, EXECUTION_ID)).thenReturn(
                state(ChatExecution.Status.RUNNING), state(ChatExecution.Status.COMPLETED),
                state(ChatExecution.Status.RUNNING), state(ChatExecution.Status.COMPLETED));
        // 처음은 상담 처리 쪽이 먼저 보냈거나 전송 실패, 다음은 여기서 보냄
        when(emitters.complete(eq(EXECUTION_ID), any())).thenReturn(false, true);

        controller.subscribe(request, SESSION_ID, EXECUTION_ID);
        verify(notifications, never()).dismissDelivered(anyLong());

        controller.subscribe(request, SESSION_ID, EXECUTION_ID);
        verify(notifications).dismissDelivered(EXECUTION_ID);
    }

    @Test
    @DisplayName("결과를 보낸 뒤 알림 삭제가 실패해도 구독 응답은 정상으로 돌려준다")
    void 알림_삭제_실패는_구독을_막지_않는다() {
        when(actors.getCurrentActor(request)).thenReturn(actor);
        when(sessions.getExecution(actor, EXECUTION_ID)).thenReturn(state(ChatExecution.Status.COMPLETED));
        when(emitters.sendTerminalNow(any(), eq("complete"), any())).thenReturn(true);
        doThrow(new IllegalStateException("db down")).when(notifications).dismissDelivered(EXECUTION_ID);

        assertThat(controller.subscribe(request, SESSION_ID, EXECUTION_ID)).isNotNull();
    }

    private static ChatExecutionState state(ChatExecution.Status status) {
        return new ChatExecutionState(SESSION_ID, EXECUTION_ID, status,
                status == ChatExecution.Status.FAILED ? "AI_PROCESSING_ERROR" : null, null);
    }
}
