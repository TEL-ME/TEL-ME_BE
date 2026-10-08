package com.telme.chat.guard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.telme.chat.service.ChatActor;
import com.telme.global.common.exception.GeneralException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChatInputGuardStatusServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-08T00:00:00Z");
    private static final ChatActor ACTOR = new ChatActor(1L, null);

    private final JdbcInputGuardStatusStore store = mock(JdbcInputGuardStatusStore.class);
    private final Clock clock = mock(Clock.class);
    private final AtomicReference<Instant> current = new AtomicReference<>(NOW);
    private final ChatInputGuardStatusService service = new ChatInputGuardStatusService(store, clock);

    @BeforeEach
    void 서버_시각을_고정한다() {
        when(clock.instant()).thenAnswer(invocation -> current.get());
    }

    @Test
    @DisplayName("제한 기록이 없으면 미제한 상태와 남은 시간 0초를 반환한다")
    void 기록이_없으면_미제한이다() {
        when(store.findRestrictionEnds(ACTOR)).thenReturn(List.of());

        var result = service.getStatus(ACTOR);

        assertThat(result.restricted()).isFalse();
        assertThat(result.restrictionUntil()).isNull();
        assertThat(result.retryAfterSeconds()).isZero();
        assertThat(result.serverTime()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("정확한 종료 시각과 이미 지난 제한은 미제한으로 반환한다")
    void 종료_시각이_현재_이하면_미제한이다() {
        when(store.findRestrictionEnds(ACTOR)).thenReturn(List.of(NOW, NOW.minusSeconds(1)));

        var result = service.getStatus(ACTOR);

        assertThat(result.restricted()).isFalse();
        assertThat(result.restrictionUntil()).isNull();
        assertThat(result.retryAfterSeconds()).isZero();
    }

    @Test
    @DisplayName("남은 시간이 정확한 정수 초이면 같은 값을 반환한다")
    void 유효한_제한은_종료_시각과_남은_시간을_반환한다() {
        Instant until = NOW.plusSeconds(40);
        when(store.findRestrictionEnds(ACTOR)).thenReturn(List.of(until));

        var result = service.getStatus(ACTOR);

        assertThat(result.restricted()).isTrue();
        assertThat(result.restrictionUntil()).isEqualTo(until);
        assertThat(result.retryAfterSeconds()).isEqualTo(40);
    }

    @Test
    @DisplayName("양수인 초 미만 시간은 올림해서 제한 중 0초로 표시하지 않는다")
    void 남은_시간은_초_단위로_올림한다() {
        when(store.findRestrictionEnds(ACTOR)).thenReturn(List.of(NOW.plusNanos(1)));

        var result = service.getStatus(ACTOR);

        assertThat(result.restricted()).isTrue();
        assertThat(result.retryAfterSeconds()).isEqualTo(1);
    }

    @Test
    @DisplayName("DB 조회가 제한 종료 시점을 넘으면 조회 후 시각으로 만료를 판단한다")
    void 조회_지연으로_끝난_제한을_반환하지_않는다() {
        when(store.findRestrictionEnds(ACTOR)).thenAnswer(invocation -> {
            current.set(NOW.plusSeconds(2));
            return List.of(NOW.plusSeconds(1));
        });

        var result = service.getStatus(ACTOR);

        assertThat(result.restricted()).isFalse();
        assertThat(result.retryAfterSeconds()).isZero();
        assertThat(result.serverTime()).isEqualTo(NOW.plusSeconds(2));
    }

    @Test
    @DisplayName("유효한 회원 본인 제한은 종료가 더 늦은 승계 게스트 제한보다 우선한다")
    void 본인_제한이_유효하면_기존_전송_판단과_같이_우선한다() {
        when(store.findRestrictionEnds(ACTOR))
                .thenReturn(List.of(NOW.plusSeconds(10), NOW.plusSeconds(60)));

        var result = service.getStatus(ACTOR);

        assertThat(result.restrictionUntil()).isEqualTo(NOW.plusSeconds(10));
        assertThat(result.retryAfterSeconds()).isEqualTo(10);
    }

    @Test
    @DisplayName("회원 본인 제한이 만료되면 승계된 게스트의 유효한 제한을 반환한다")
    void 본인_제한이_끝나면_유효한_승계_제한을_조회한다() {
        when(store.findRestrictionEnds(ACTOR))
                .thenReturn(List.of(NOW, NOW.plusSeconds(60), NOW.plusSeconds(20)));

        var result = service.getStatus(ACTOR);

        assertThat(result.restrictionUntil()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    @DisplayName("식별자가 없으면 DB 조회 없이 기존 인증 오류로 처리한다")
    void 식별자가_없으면_조회하지_않는다() {
        assertThatThrownBy(() -> service.getStatus(new ChatActor(null, null)))
                .isInstanceOf(GeneralException.class);

        verifyNoInteractions(store, clock);
    }

    @Test
    @DisplayName("조회 오류를 미제한 성공 응답으로 바꾸지 않는다")
    void 저장소_오류는_미제한으로_추정하지_않는다() {
        when(store.findRestrictionEnds(ACTOR)).thenThrow(new IllegalStateException("조회 오류"));

        assertThatThrownBy(() -> service.getStatus(ACTOR))
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(clock);
    }
}
