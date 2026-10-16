package com.telme.chat.dto.res;

public record AdminChatMonitoringStageResponse(
        // PASSED, PARTIAL, FAILED, SKIPPED, RUNNING
        String state,
        // 짧은 라벨(예: "3건")
        String label
) {
}
