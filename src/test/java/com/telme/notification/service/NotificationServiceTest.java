package com.telme.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telme.chat.exception.ChatErrorCode;
import com.telme.chat.repository.ChatSessionRepository;
import com.telme.chat.service.ChatActor;
import com.telme.global.common.exception.GeneralException;
import com.telme.notification.dto.NotificationType;
import com.telme.notification.exception.NotificationErrorCode;
import com.telme.notification.repository.NotificationRepository;
import com.telme.notification.repository.NotificationRow;
import com.telme.notification.service.NotificationService.NotificationPage;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificationServiceTest {

    private static final NotificationType TYPE = NotificationType.ANSWER_ARRIVED;

    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final ChatSessionRepository sessions = mock(ChatSessionRepository.class);
    private final NotificationService service = new NotificationService(notifications, sessions);
    private final ChatActor member = new ChatActor(1L, null);

    @Test
    @DisplayName("새로 저장하면 저장한 알림을 돌려준다")
    void 새로_저장한_알림을_돌려준다() {
        NotificationRow saved = row(7L, 10L, 100L);
        when(notifications.insert(member, 10L, 100L, TYPE)).thenReturn(Optional.of(7L));
        when(notifications.findById(member, 7L)).thenReturn(Optional.of(saved));

        assertThat(service.save(member, TYPE, 10L, 100L)).isEqualTo(saved);
    }

    @Test
    @DisplayName("저장 대상이 아니면 404")
    void 저장할_수_없으면_404() {
        when(notifications.insert(any(), anyLong(), anyLong(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.save(member, TYPE, 11L, 100L))
                .extracting(e -> ((GeneralException) e).getErrorCode())
                .isEqualTo(NotificationErrorCode.TARGET_UNAVAILABLE);
    }

    @Test
    @DisplayName("한 개 더 읽어 다음 페이지가 있으면 마지막 알림 번호를 커서로 준다")
    void 다음_페이지_커서() {
        when(notifications.findPage(member, Long.MAX_VALUE, 3))
                .thenReturn(List.of(row(9L, 1L, 1L), row(8L, 1L, 2L), row(7L, 1L, 3L)));
        when(notifications.findPage(member, 8L, 3)).thenReturn(List.of(row(7L, 1L, 3L)));

        NotificationPage first = service.getPage(member, null, 2);
        assertThat(first.items()).extracting(NotificationRow::notificationId).containsExactly(9L, 8L);
        assertThat(first.hasNext()).isTrue();
        assertThat(first.nextCursor()).isEqualTo(8L);

        NotificationPage last = service.getPage(member, 8L, 2);
        assertThat(last.items()).extracting(NotificationRow::notificationId).containsExactly(7L);
        assertThat(last.hasNext()).isFalse();
        assertThat(last.nextCursor()).isNull();
    }

    @Test
    @DisplayName("남의 채팅방은 읽음 처리하지 않고 채팅 API와 같은 404를 준다")
    void 남의_채팅방은_404() {
        when(sessions.existsBySessionIdAndUserId(10L, 1L)).thenReturn(false);

        assertThatThrownBy(() -> service.readSession(member, 10L))
                .extracting(e -> ((GeneralException) e).getErrorCode())
                .isEqualTo(ChatErrorCode.SESSION_NOT_FOUND);
        verify(notifications, never()).markSessionRead(any(), anyLong());
    }

    @Test
    @DisplayName("게스트는 게스트 기준으로 세션 주인을 확인한다")
    void 게스트_채팅방_읽음() {
        UUID guestId = UUID.randomUUID();
        ChatActor guest = new ChatActor(null, guestId);
        when(sessions.existsBySessionIdAndUserIdIsNullAndGuestId(10L, guestId)).thenReturn(true);
        when(notifications.markSessionRead(guest, 10L)).thenReturn(2);

        assertThat(service.readSession(guest, 10L)).isEqualTo(2);
    }

    private static NotificationRow row(long id, long sessionId, long messageId) {
        return new NotificationRow(id, "ANSWER_ARRIVED", sessionId, messageId, null, "답변", Instant.EPOCH, null);
    }
}
