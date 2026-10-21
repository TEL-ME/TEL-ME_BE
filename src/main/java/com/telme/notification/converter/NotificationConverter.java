package com.telme.notification.converter;

import com.telme.notification.dto.NotificationType;
import com.telme.notification.dto.res.NotificationListResponse;
import com.telme.notification.dto.res.NotificationReadResponse;
import com.telme.notification.dto.res.NotificationResponse;
import com.telme.notification.dto.res.NotificationUnreadCountResponse;
import com.telme.notification.repository.NotificationRow;
import com.telme.notification.service.NotificationService.NotificationPage;
import org.springframework.stereotype.Component;

@Component
public class NotificationConverter {

    // 알림 본문으로 보여 줄 답변 앞부분 길이
    static final int BODY_LENGTH = 50;

    public NotificationResponse toResponse(NotificationRow row) {
        return new NotificationResponse(
                row.notificationId(),
                NotificationType.valueOf(row.type()),
                row.sessionTitle(),
                body(row.messagePreview()),
                row.sessionId(),
                row.messageId(),
                row.createdAt(),
                row.readAt() != null);
    }

    public NotificationListResponse toListResponse(NotificationPage page) {
        return new NotificationListResponse(
                page.items().stream().map(this::toResponse).toList(), page.nextCursor(), page.hasNext());
    }

    public NotificationUnreadCountResponse toUnreadCountResponse(long unreadCount) {
        return new NotificationUnreadCountResponse(unreadCount);
    }

    public NotificationReadResponse toReadResponse(int readCount) {
        return new NotificationReadResponse(readCount);
    }

    // 목록 한 줄에 맞게 줄바꿈을 공백으로 바꾸고 줄인다
    private static String body(String preview) {
        if (preview == null) {
            return null;
        }
        String text = preview.strip().replaceAll("\\s+", " ");
        if (text.length() <= BODY_LENGTH) {
            return text;
        }
        // 이모지 반쪽 잘림 방지
        int end = Character.isHighSurrogate(text.charAt(BODY_LENGTH - 1)) ? BODY_LENGTH - 1 : BODY_LENGTH;
        return text.substring(0, end) + "...";
    }
}
