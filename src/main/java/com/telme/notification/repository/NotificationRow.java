package com.telme.notification.repository;

import java.time.Instant;

// 알림 한 줄(조회 결과). 채팅방 제목과 답변 앞부분은 저장하지 않고 조회할 때 붙인다
public record NotificationRow(
        long notificationId,
        String type,
        long sessionId,
        long messageId,
        // 제목 생성 전 채팅방은 null
        String sessionTitle,
        // 답변 앞부분(NotificationRepository.PREVIEW_LENGTH자까지)
        String messagePreview,
        Instant createdAt,
        // null이면 안 읽음
        Instant readAt) {
}
