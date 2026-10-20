package com.telme.chat.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
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
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

// 늦은 구독과 상담 처리 완료가 어느 순서로 오든, 결과를 받은 실행에 알림이 남지 않는지 실제 DB로 확인한다.
// 메모리 잠금 없이 알림 행(숨김 표시)과 고유 키로 맞추므로 서버가 여러 대여도 같은 결과다
@SpringBootTest
@Transactional
class LateDeliveryNotificationRaceTest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private JdbcTemplate jdbc;

    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final ChatActorProvider actors = mock(ChatActorProvider.class);
    private final ChatSessionService sessions = mock(ChatSessionService.class);
    private final ChatEmitterRegistry registry = new ChatEmitterRegistry();

    private ChatEmitterConsultEvents events;
    private ChatExecutionController controller;
    private ChatActor actor;
    private long sessionId;
    private long answerId;
    private long executionId;

    @BeforeEach
    void setUp() {
        long userId = jdbc.queryForObject("INSERT INTO users (name) VALUES ('race') RETURNING user_id", Long.class);
        actor = new ChatActor(userId, null);
        sessionId = jdbc.queryForObject("INSERT INTO chat_sessions (user_id) VALUES (?) RETURNING session_id",
                Long.class, userId);
        events = new ChatEmitterConsultEvents(registry, notificationService::saveUndelivered);
        controller = new ChatExecutionController(actors, sessions, registry,
                new ChatExecutionProperties(Duration.ofMinutes(5)), mock(ChatExecutionTraceService.class),
                new StaticListableBeanFactory(Map.of("notificationService", notificationService))
                        .getBeanProvider(NotificationService.class));
        when(actors.getCurrentActor(request)).thenReturn(actor);
    }

    @Test
    @DisplayName("구독 등록 직후 끝나 늦은 구독이 먼저 전하면, 뒤늦은 상담 완료는 알림을 남기지 않는다")
    void 등록_후_재확인_경로가_먼저_전함() {
        execution("COMPLETED", "ANSWER");
        when(sessions.getExecution(actor, executionId))
                .thenReturn(state(ChatExecution.Status.RUNNING), state(ChatExecution.Status.COMPLETED));

        controller.subscribe(request, sessionId, executionId);
        events.completed(executionId, state(ChatExecution.Status.COMPLETED));

        assertThat(visibleNotifications()).isZero();
    }

    @Test
    @DisplayName("이미 끝난 실행을 즉시 종료 경로로 먼저 전하면, 뒤늦은 실패 처리도 알림을 남기지 않는다")
    void 즉시_종료_경로가_먼저_전함() {
        execution("FAILED", "ERROR");
        when(sessions.getExecution(actor, executionId)).thenReturn(state(ChatExecution.Status.FAILED));

        controller.subscribe(request, sessionId, executionId);
        events.failed(executionId, new ChatFailure(ChatMessage.Status.FAILED, "AI_PROCESSING_ERROR"));

        assertThat(visibleNotifications()).isZero();
    }

    @Test
    @DisplayName("상담 완료가 먼저 와서 못 전하면 알림을 남기고, 늦은 구독이 결과를 전하면 그 알림을 숨긴다")
    void 상담_완료가_먼저() {
        execution("COMPLETED", "ANSWER");
        when(sessions.getExecution(actor, executionId)).thenReturn(state(ChatExecution.Status.COMPLETED));

        events.completed(executionId, state(ChatExecution.Status.COMPLETED));
        assertThat(visibleNotifications()).isEqualTo(1);

        controller.subscribe(request, sessionId, executionId);
        assertThat(visibleNotifications()).isZero();
    }

    @Test
    @DisplayName("늦은 구독으로 결과를 받은 프론트가 알림을 저장하면 그 판단대로 다시 보인다")
    void 결과를_받은_프론트_저장이_우선한다() {
        execution("COMPLETED", "ANSWER");
        when(sessions.getExecution(actor, executionId)).thenReturn(state(ChatExecution.Status.COMPLETED));

        controller.subscribe(request, sessionId, executionId);
        notificationService.save(actor, NotificationType.ANSWER_ARRIVED, sessionId, answerId);
        events.completed(executionId, state(ChatExecution.Status.COMPLETED));

        assertThat(visibleNotifications()).isEqualTo(1);
    }

    private void execution(String status, String type) {
        long question = jdbc.queryForObject("INSERT INTO chat_messages (session_id, sequence_no, role, message_type,"
                + " status, content) VALUES (?, 1, 'USER', 'QUESTION', 'COMPLETED', '질문') RETURNING message_id",
                Long.class, sessionId);
        answerId = jdbc.queryForObject("INSERT INTO chat_messages (session_id, sequence_no, role, message_type,"
                + " status, content) VALUES (?, 2, 'ASSISTANT', ?, ?, '답변') RETURNING message_id",
                Long.class, sessionId, type, status);
        executionId = jdbc.queryForObject("INSERT INTO chat_executions (session_id, input_message_id,"
                + " output_message_id, status) VALUES (?, ?, ?, ?) RETURNING execution_id",
                Long.class, sessionId, question, answerId, status);
    }

    private long visibleNotifications() {
        return notificationService.getPage(actor, null, 20).items().size();
    }

    private ChatExecutionState state(ChatExecution.Status status) {
        return new ChatExecutionState(sessionId, executionId, status,
                status == ChatExecution.Status.FAILED ? "AI_PROCESSING_ERROR" : null, null);
    }
}
