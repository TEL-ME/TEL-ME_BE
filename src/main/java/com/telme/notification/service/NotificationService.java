package com.telme.notification.service;

import com.telme.chat.exception.ChatErrorCode;
import com.telme.chat.repository.ChatSessionRepository;
import com.telme.chat.service.ChatActor;
import com.telme.global.common.exception.GeneralException;
import com.telme.notification.dto.NotificationType;
import com.telme.notification.exception.NotificationErrorCode;
import com.telme.notification.repository.NotificationRepository;
import com.telme.notification.repository.NotificationRow;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final ChatSessionRepository chatSessionRepository;

    // 이미 있으면 기존 알림을 돌려준다
    @Transactional
    public NotificationRow save(ChatActor actor, NotificationType type, long sessionId, long messageId) {
        return notificationRepository.insert(actor, sessionId, messageId, type)
                .flatMap(id -> notificationRepository.findById(actor, id))
                .orElseThrow(() -> new GeneralException(NotificationErrorCode.TARGET_UNAVAILABLE));
    }

    // 종료 결과를 전하지 못한 실행의 알림을 남긴다
    @Transactional
    public void saveUndelivered(long executionId, NotificationType type) {
        notificationRepository.insertForExecution(executionId, type);
    }

    // 늦은 구독에 결과를 전한 뒤 서버 알림만 지운다
    @Transactional
    public void dismissDelivered(long executionId) {
        notificationRepository.deleteServerSaved(executionId);
    }

    public NotificationPage getPage(ChatActor actor, Long cursor, int size) {
        // size+1개 읽어 다음 페이지 여부 판단
        List<NotificationRow> rows = notificationRepository.findPage(
                actor, cursor == null ? Long.MAX_VALUE : cursor, size + 1);
        boolean hasNext = rows.size() > size;
        List<NotificationRow> page = rows.subList(0, Math.min(size, rows.size()));
        Long nextCursor = hasNext ? page.getLast().notificationId() : null;
        return new NotificationPage(page, nextCursor, hasNext);
    }

    public long countUnread(ChatActor actor) {
        return notificationRepository.countUnread(actor);
    }

    // 채팅방에 들어가면 부른다. 읽음 처리한 개수를 돌려준다
    @Transactional
    public int readSession(ChatActor actor, long sessionId) {
        validateSessionOwner(actor, sessionId);
        return notificationRepository.markSessionRead(actor, sessionId);
    }

    // 채팅 API와 같은 기준·오류 코드로 남의 세션을 막는다
    private void validateSessionOwner(ChatActor actor, long sessionId) {
        boolean owned = actor.isMember()
                ? chatSessionRepository.existsBySessionIdAndUserId(sessionId, actor.userId())
                : chatSessionRepository.existsBySessionIdAndUserIdIsNullAndGuestId(sessionId, actor.guestId());
        if (!owned) {
            throw new GeneralException(ChatErrorCode.SESSION_NOT_FOUND);
        }
    }

    public record NotificationPage(List<NotificationRow> items, Long nextCursor, boolean hasNext) {
    }
}
