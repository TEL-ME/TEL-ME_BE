package com.telme.chat.dto.req;

// 목록 상태 필터(BLOCKED는 메시지 유형 기준)
public enum AdminChatMonitoringStatus {
    RUNNING, COMPLETED, FAILED, CANCELLED, BLOCKED
}
