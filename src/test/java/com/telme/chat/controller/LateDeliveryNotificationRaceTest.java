package com.telme.chat.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telme.chat.config.ChatExecutionProperties;
import com.telme.chat.entity.ChatExecution;
import com.telme.chat.entity.ChatMessage;
import com.telme.chat.service.ChatActor;
import com.telme.chat.service.ChatActorProvider;
import com.telme.chat.service.ChatEmitterRegistry;
import com.telme.chat.service.ChatExecutionState;
import com.telme.chat.service.ChatExecutionTraceService;
import com.telme.chat.service.ChatFailure;
import com.telme.chat.service.ChatSessionService;
import com.telme.consult.service.ChatEmitterConsultEvents;
import com.telme.notification.dto.NotificationType;
import com.telme.notification.service.NotificationService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

// 늦은 구독 경로와 상담 처리 완료가 어느 순서로 오든, 결과를 받은 실행에 서버 알림이 남지 않는지 실제 레지스트리로 확인한다
class LateDeliveryNotificationRaceTest {

    private static final long SESSION_ID = 1L;
    private static final long EXECUTION_ID = 2L;

    private final ChatActor actor = new ChatActor(10L, null);
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final ChatActorProvider actors = mock(ChatActorProvider.class);
    private final ChatSessionService sessions = mock(ChatSessionService.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final ChatEmitterRegistry registry = new ChatEmitterRegistry();
    private final List<NotificationType> saved = new ArrayList<>();
    private final ChatEmitterConsultEvents events =
            new ChatEmitterConsultEvents(registry, (executionId, type) -> saved.add(type));
    private final ChatExecutionController controller = new ChatExecutionController(
            actors, sessions, registry, new ChatExecutionProperties(Duration.ofMinutes(5)),
            mock(ChatExecutionTraceService.class),
            new StaticListableBeanFactory(Map.of("notificationService", notifications))
                    .getBeanProvider(NotificationService.class));

    @Test
    @DisplayName("구독 등록 직후 완료가 저장돼 늦은 구독 경로가 먼저 전하면, 뒤늦은 상담 완료는 알림을 남기지 않는다")
    void 등록_후_재확인_경로가_먼저_전함() {
        when(actors.getCurrentActor(request)).thenReturn(actor);
        when(sessions.getExecution(actor, EXECUTION_ID))
                .thenReturn(state(ChatExecution.Status.RUNNING), state(ChatExecution.Status.COMPLETED));

        controller.subscribe(request, SESSION_ID, EXECUTION_ID);
        events.completed(EXECUTION_ID, state(ChatExecution.Status.COMPLETED));

        assertThat(saved).isEmpty();
        assertThat(registry.isRegistered(EXECUTION_ID)).isFalse();
    }

    @Test
    @DisplayName("이미 끝난 실행을 즉시 종료 경로로 먼저 전하면, 뒤늦은 상담 완료·실패는 알림을 남기지 않는다")
    void 즉시_종료_경로가_먼저_전함() {
        when(actors.getCurrentActor(request)).thenReturn(actor);
        when(sessions.getExecution(actor, EXECUTION_ID)).thenReturn(state(ChatExecution.Status.FAILED));

        controller.subscribe(request, SESSION_ID, EXECUTION_ID);
        events.failed(EXECUTION_ID, new ChatFailure(ChatMessage.Status.FAILED, "AI_PROCESSING_ERROR"));

        assertThat(saved).isEmpty();
    }

    @Test
    @DisplayName("상담 완료가 먼저 와서 못 전하면 알림을 남기고, 늦은 구독이 결과를 전하면 그 알림을 지운다")
    void 상담_완료가_먼저() {
        when(actors.getCurrentActor(request)).thenReturn(actor);
        when(sessions.getExecution(actor, EXECUTION_ID)).thenReturn(state(ChatExecution.Status.COMPLETED));

        events.completed(EXECUTION_ID, state(ChatExecution.Status.COMPLETED));
        assertThat(saved).containsExactly(NotificationType.ANSWER_ARRIVED);

        controller.subscribe(request, SESSION_ID, EXECUTION_ID);
        verify(notifications).dismissDelivered(EXECUTION_ID);
    }

    @Test
    @DisplayName("늦은 전달 기록은 한 번 확인하면 지워져, 같은 실행의 다음 완료 판단에는 남지 않는다")
    void 늦은_전달_기록은_한_번만_쓴다() {
        when(actors.getCurrentActor(request)).thenReturn(actor);
        when(sessions.getExecution(actor, EXECUTION_ID)).thenReturn(state(ChatExecution.Status.COMPLETED));

        controller.subscribe(request, SESSION_ID, EXECUTION_ID);
        events.completed(EXECUTION_ID, state(ChatExecution.Status.COMPLETED));
        events.completed(EXECUTION_ID, state(ChatExecution.Status.COMPLETED));

        assertThat(saved).containsExactly(NotificationType.ANSWER_ARRIVED);
    }

    private static ChatExecutionState state(ChatExecution.Status status) {
        return new ChatExecutionState(SESSION_ID, EXECUTION_ID, status,
                status == ChatExecution.Status.FAILED ? "AI_PROCESSING_ERROR" : null, null);
    }
}
