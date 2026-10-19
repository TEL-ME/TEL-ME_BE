package com.telme.notification.dto;

// 알림 종류. 늘릴 때는 notifications.type CHECK 제약도 함께 바꾼다
public enum NotificationType {
    // 새 답변(되묻기·매장 결과 포함)이 도착함
    ANSWER_ARRIVED,
    // 답변을 만들지 못함(실패·시간 초과). 사용자가 떠나 취소된 답변은 알리지 않는다
    ANSWER_FAILED
}
