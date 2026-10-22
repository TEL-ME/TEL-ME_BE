package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.telme.chat.config.ChatGuestLimitProperties;
import com.telme.chat.dto.res.ChatGuestLimitResponse;
import com.telme.chat.exception.ChatErrorCode;
import com.telme.chat.repository.ChatMessageRepository;
import com.telme.global.common.exception.GeneralException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GuestDailyChatLimitTest {

    // 2026-10-22 23:30 KST(14:30 UTC). 다음 한국 시간 0시는 2026-10-22T15:00:00Z
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-22T14:30:00Z"), ZoneOffset.UTC);

    private final ChatMessageRepository repository = mock(ChatMessageRepository.class);
    private final UUID guestId = UUID.randomUUID();

    @Test
    void 오늘_질문_수가_한도보다_적으면_통과하고_접수_뒤_남는_수를_돌려준다() {
        when(repository.countGuestQuestionsToday(guestId)).thenReturn(7L);

        assertThat(limit(10).check(new ChatActor(null, guestId))).isEqualTo(2);
    }

    @Test
    void 마지막_한_번이면_남는_수는_0이다() {
        when(repository.countGuestQuestionsToday(guestId)).thenReturn(9L);

        assertThat(limit(10).check(new ChatActor(null, guestId))).isZero();
    }

    @Test
    void 오늘_질문_수가_한도에_닿으면_거절한다() {
        when(repository.countGuestQuestionsToday(guestId)).thenReturn(10L);

        assertThatThrownBy(() -> limit(10).check(new ChatActor(null, guestId)))
                .isInstanceOfSatisfying(GeneralException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ChatErrorCode.GUEST_DAILY_LIMIT_EXCEEDED));
    }

    @Test
    void 회원은_세지_않는다() {
        assertThat(limit(10).check(new ChatActor(1L, null))).isNull();
        assertThat(limit(10).status(new ChatActor(1L, null))).isEqualTo(ChatGuestLimitResponse.unlimited());

        verifyNoInteractions(repository);
    }

    @Test
    void 한도가_0이면_세지_않는다() {
        assertThat(limit(0).check(new ChatActor(null, guestId))).isNull();
        assertThat(limit(0).status(new ChatActor(null, guestId))).isEqualTo(ChatGuestLimitResponse.unlimited());

        verifyNoInteractions(repository);
    }

    @Test
    void 조회는_남은_수와_다음_한국_시간_0시를_준다() {
        when(repository.countGuestQuestionsToday(guestId)).thenReturn(7L);

        assertThat(limit(10).status(new ChatActor(null, guestId))).isEqualTo(
                new ChatGuestLimitResponse(true, 10, 3, Instant.parse("2026-10-22T15:00:00Z")));
    }

    @Test
    void 한도를_넘겨_센_경우에도_남은_수는_0_아래로_내려가지_않는다() {
        when(repository.countGuestQuestionsToday(guestId)).thenReturn(12L);

        assertThat(limit(10).status(new ChatActor(null, guestId)).remaining()).isZero();
    }

    @Test
    void 음수_한도는_받지_않는다() {
        assertThatThrownBy(() -> new ChatGuestLimitProperties(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    private GuestDailyChatLimit limit(int dailyMessages) {
        return new GuestDailyChatLimit(repository, new ChatGuestLimitProperties(dailyMessages), CLOCK);
    }
}
