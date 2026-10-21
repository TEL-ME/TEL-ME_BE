package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.telme.chat.config.ChatGuestLimitProperties;
import com.telme.chat.exception.ChatErrorCode;
import com.telme.chat.repository.ChatMessageRepository;
import com.telme.global.common.exception.GeneralException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GuestDailyChatLimitTest {

    private final ChatMessageRepository repository = mock(ChatMessageRepository.class);
    private final UUID guestId = UUID.randomUUID();

    @Test
    void 오늘_질문_수가_한도보다_적으면_통과한다() {
        when(repository.countGuestQuestionsToday(guestId)).thenReturn(9L);

        assertThatCode(() -> limit(10).check(new ChatActor(null, guestId))).doesNotThrowAnyException();
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
        limit(10).check(new ChatActor(1L, null));

        verifyNoInteractions(repository);
    }

    @Test
    void 한도가_0이면_세지_않는다() {
        limit(0).check(new ChatActor(null, guestId));

        verifyNoInteractions(repository);
    }

    @Test
    void 음수_한도는_받지_않는다() {
        assertThatThrownBy(() -> new ChatGuestLimitProperties(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    private GuestDailyChatLimit limit(int dailyMessages) {
        return new GuestDailyChatLimit(repository, new ChatGuestLimitProperties(dailyMessages));
    }
}
