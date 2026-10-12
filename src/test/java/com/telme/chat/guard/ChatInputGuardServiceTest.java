package com.telme.chat.guard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telme.chat.dto.req.ChatMessageSendRequest;
import com.telme.chat.dto.res.ChatMessageSendResponse;
import com.telme.chat.guard.JdbcInputGuardStore.Cached;
import com.telme.chat.guard.JdbcInputGuardStore.State;
import com.telme.chat.service.ChatActor;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChatInputGuardServiceTest {

    private static final Instant START = Instant.parse("2026-10-08T00:00:00Z");
    private static final ChatActor ACTOR = new ChatActor(1L, null);

    private final ChatInputInspector inspector = mock(ChatInputInspector.class);
    private final JdbcInputGuardStore store = mock(JdbcInputGuardStore.class);
    private final Clock clock = mock(Clock.class);
    private final AtomicReference<Instant> current = new AtomicReference<>(START);

    private ChatInputGuardService service;

    @BeforeEach
    void 준비한다() {
        when(clock.instant()).thenAnswer(invocation -> current.get());
        when(store.cached(any(State.class), any())).thenReturn(Optional.empty());
        when(store.resetExpiredAndInherit(any(State.class), any(Instant.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(store.restrict(any(State.class), any(Instant.class), any(Instant.class)))
                .thenAnswer(invocation -> {
                    State state = invocation.getArgument(0);
                    return new State(state.id(), state.userId(), state.guestId(), state.countingFrom(),
                            invocation.getArgument(1), invocation.getArgument(2));
                });
        service = new ChatInputGuardService(
                inspector,
                store,
                new InputGuardProperties(
                        Duration.ofMinutes(10), 3, Duration.ofMinutes(1), Duration.ofDays(30)),
                clock);
    }

    @Test
    @DisplayName("잠금 대기 중 제한이 만료되면 획득한 시각으로 정상 접수한다")
    void 잠금_대기가_만료를_넘으면_이미_끝난_제한을_반환하지_않는다() {
        State state = 제한_상태(START.plusSeconds(1));
        Instant acquired = START.plusSeconds(2);
        잠금_획득_시각을_설정한다(state, acquired);
        when(inspector.inspect("문의"))
                .thenReturn(new InputInspection("문의", List.of(), true));

        var turn = service.begin(ACTOR, 3L, new ChatMessageSendRequest("문의"));

        assertThat(turn.rejected()).isFalse();
        assertThat(turn.notice()).isNull();
        assertThat(turn.now()).isEqualTo(acquired);
        verify(store).resetExpiredAndInherit(state, acquired);
        verify(store).count(state, acquired, acquired.minusSeconds(600));
    }

    @Test
    @DisplayName("잠금을 기다린 시간만큼 남은 제한 시간이 줄고 종료 시각은 유지된다")
    void 제한_응답은_잠금_획득_후의_남은_시간을_반환한다() {
        Instant until = START.plusSeconds(60);
        State state = 제한_상태(until);
        잠금_획득_시각을_설정한다(state, START.plusSeconds(20));
        when(inspector.inspect("문의"))
                .thenReturn(new InputInspection("문의", List.of(), true));

        var turn = service.begin(ACTOR, 3L, new ChatMessageSendRequest("문의"));

        assertThat(turn.rejected()).isTrue();
        assertThat(turn.notice().retryAfterSeconds()).isEqualTo(40);
        assertThat(turn.notice().restrictionUntil()).isEqualTo(until);
    }

    @Test
    @DisplayName("동일 제한 요청 재전송도 잠금 대기 후 남은 시간만 갱신한다")
    void 재전송은_원래_조치와_횟수를_유지하고_대기_후_시간을_반환한다() {
        State state = new State(1L, 1L, null, Instant.EPOCH, null, null);
        when(store.lock(any(ChatActor.class), any(Instant.class))).thenReturn(state);
        when(store.count(eq(state), any(Instant.class), any(Instant.class))).thenReturn(2);
        when(inspector.inspect("ㅅㅂ"))
                .thenReturn(new InputInspection(
                        "ㅅㅂ",
                        List.of(new InputInspection.Detection(
                                InputInspection.Reason.INITIAL_PROFANITY, "INITIAL_SB")),
                        true));
        var request = new ChatMessageSendRequest("ㅅㅂ", null, null, UUID.randomUUID());
        var original = service.begin(ACTOR, 3L, request);
        var response = new ChatMessageSendResponse(
                3L, 4L, 1, null, null, START, original.notice());
        when(store.cached(state, request.requestId()))
                .thenReturn(Optional.of(new Cached(original.fingerprint(), response)));
        잠금_획득_시각을_설정한다(state, START.plusSeconds(20));

        var replay = service.begin(ACTOR, 3L, request).cached();

        assertThat(replay.inputGuard().retryAfterSeconds()).isEqualTo(40);
        assertThat(replay.inputGuard().action()).isEqualTo("RESTRICTED");
        assertThat(replay.inputGuard().violationCount()).isEqualTo(3);
        assertThat(replay.inputGuard().restrictionUntil()).isEqualTo(START.plusSeconds(60));
        assertThat(replay.messageId()).isEqualTo(4L);
        assertThat(replay.createdAt()).isEqualTo(START);
        verify(store, times(1)).restrict(state, START, START.plusSeconds(60));
        verify(store, times(1)).count(eq(state), any(Instant.class), any(Instant.class));
    }

    @Test
    @DisplayName("새 제한의 시작과 종료는 잠금 획득 시각을 기준으로 정한다")
    void 새_제한은_잠금_대기_시간으로_줄어들지_않는다() {
        State state = new State(1L, 1L, null, Instant.EPOCH, null, null);
        Instant acquired = START.plusSeconds(20);
        잠금_획득_시각을_설정한다(state, acquired);
        when(store.count(eq(state), any(Instant.class), any(Instant.class))).thenReturn(2);
        when(inspector.inspect("ㅅㅂ"))
                .thenReturn(new InputInspection(
                        "ㅅㅂ",
                        List.of(new InputInspection.Detection(
                                InputInspection.Reason.INITIAL_PROFANITY, "INITIAL_SB")),
                        true));

        var turn = service.begin(ACTOR, 3L, new ChatMessageSendRequest("ㅅㅂ"));

        assertThat(turn.notice().restrictionStartedAt()).isEqualTo(acquired);
        assertThat(turn.notice().restrictionUntil()).isEqualTo(acquired.plusSeconds(60));
        assertThat(turn.notice().retryAfterSeconds()).isEqualTo(60);
        verify(store).restrict(state, acquired, acquired.plusSeconds(60));
    }

    private State 제한_상태(Instant until) {
        return new State(1L, 1L, null, Instant.EPOCH, START.minusSeconds(59), until);
    }

    private void 잠금_획득_시각을_설정한다(State state, Instant acquired) {
        // 잠금을 기다리는 동안 시각이 진행되는 경계를 대기 없이 재현한다.
        when(store.lock(any(ChatActor.class), any(Instant.class))).thenAnswer(invocation -> {
            current.set(acquired);
            return state;
        });
    }
}
